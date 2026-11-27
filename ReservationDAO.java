import java.sql.*;
import java.util.*;

/** CRUD JDBC sur les réservations. La détection de conflit est faite DANS la base (transaction + verrou). */
public class ReservationDAO {

    /** Levée quand le créneau demandé est indisponible. */
    public static class ConflictException extends Exception {
        public ConflictException(String message) { super(message); }
    }

    private static final String SELECT =
        "SELECT r.id, r.idUtilisateur, r.idSalle, r.date, r.heureDebut, r.heureFin, r.motif, r.statut, s.nom, u.nom "
      + "FROM Reservation r JOIN Salle s ON s.id = r.idSalle JOIN Utilisateur u ON u.id = r.idUtilisateur ";

    private Reservation map(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setId(rs.getInt(1));
        r.setIdUtilisateur(rs.getInt(2));
        r.setIdSalle(rs.getInt(3));
        r.setDate(rs.getDate(4).toLocalDate());
        r.setHeureDebut(rs.getTime(5).toLocalTime());
        r.setHeureFin(rs.getTime(6).toLocalTime());
        r.setMotif(rs.getString(7));
        r.setStatut(rs.getString(8));
        r.setNomSalle(rs.getString(9));
        r.setNomUtilisateur(rs.getString(10));
        return r;
    }

    // ---------- READ ----------
    /** @param userId null = toutes les réservations (admin) ; @param keyword null/vide = pas de recherche */
    public List<Reservation> findAll(Integer userId, String keyword) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            sql.append("AND r.idUtilisateur = ? ");
            params.add(userId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (s.nom LIKE ? OR u.nom LIKE ? OR r.motif LIKE ? OR CAST(r.date AS CHAR) LIKE ? OR r.statut LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) params.add(k);
        }
        sql.append("ORDER BY r.date DESC, r.heureDebut");

        List<Reservation> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public Reservation findById(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE r.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    // ---------- CREATE ----------
    public int create(Reservation r) throws SQLException, ConflictException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                lockAndCheck(c, r, 0);
                String sql = "INSERT INTO Reservation (idUtilisateur, idSalle, date, heureDebut, heureFin, motif, statut) "
                           + "VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMEE')";
                int id;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, r.getIdUtilisateur());
                    ps.setInt(2, r.getIdSalle());
                    ps.setDate(3, java.sql.Date.valueOf(r.getDate()));
                    ps.setTime(4, Time.valueOf(r.getHeureDebut()));
                    ps.setTime(5, Time.valueOf(r.getHeureFin()));
                    ps.setString(6, r.getMotif());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); id = keys.getInt(1); }
                }
                c.commit();
                return id;
            } catch (SQLException | ConflictException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ---------- UPDATE ----------
    public int update(Reservation r) throws SQLException, ConflictException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                lockAndCheck(c, r, r.getId());
                String sql = "UPDATE Reservation SET idSalle = ?, date = ?, heureDebut = ?, heureFin = ?, motif = ? WHERE id = ?";
                int n;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, r.getIdSalle());
                    ps.setDate(2, java.sql.Date.valueOf(r.getDate()));
                    ps.setTime(3, Time.valueOf(r.getHeureDebut()));
                    ps.setTime(4, Time.valueOf(r.getHeureFin()));
                    ps.setString(5, r.getMotif());
                    ps.setInt(6, r.getId());
                    n = ps.executeUpdate();
                }
                c.commit();
                return n;
            } catch (SQLException | ConflictException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ---------- CANCEL / DELETE ----------
    public int cancel(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE Reservation SET statut = 'ANNULEE' WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    public int delete(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM Reservation WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    // ---------- ANTI-CONFLIT (garanti par la base) ----------
    /**
     * 1) Verrouille la ligne de la salle (SELECT ... FOR UPDATE) : deux transactions visant la même salle
     *    sont donc sérialisées par MySQL, quel que soit le nombre de serveurs/threads.
     * 2) Compte les réservations CONFIRMEES qui chevauchent le créneau : debutExistant < finNouveau ET finExistant > debutNouveau.
     */
    private void lockAndCheck(Connection c, Reservation r, int ignoreId) throws SQLException, ConflictException {
        try (PreparedStatement ps = c.prepareStatement("SELECT disponible FROM Salle WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, r.getIdSalle());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ConflictException("Salle introuvable.");
                if (!rs.getBoolean(1)) throw new ConflictException("Cette salle n'est pas disponible à la réservation.");
            }
        }
        String sql = "SELECT COUNT(*) FROM Reservation WHERE idSalle = ? AND date = ? AND statut = 'CONFIRMEE' "
                   + "AND id <> ? AND heureDebut < ? AND heureFin > ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, r.getIdSalle());
            ps.setDate(2, java.sql.Date.valueOf(r.getDate()));
            ps.setInt(3, ignoreId);
            ps.setTime(4, Time.valueOf(r.getHeureFin()));
            ps.setTime(5, Time.valueOf(r.getHeureDebut()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    throw new ConflictException("Salle indisponible : un créneau qui chevauche "
                            + r.getHeureDebut() + "-" + r.getHeureFin() + " est déjà réservé ce jour-là.");
                }
            }
        }
    }

    // ---------- STATISTIQUES ----------
    public Map<String, Integer> stats() throws SQLException {
        Map<String, Integer> m = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            m.put("Réservations (total)", scalar(st, "SELECT COUNT(*) FROM Reservation"));
            m.put("Réservations confirmées", scalar(st, "SELECT COUNT(*) FROM Reservation WHERE statut = 'CONFIRMEE'"));
            m.put("Réservations annulées", scalar(st, "SELECT COUNT(*) FROM Reservation WHERE statut = 'ANNULEE'"));
            m.put("Réservations aujourd'hui", scalar(st, "SELECT COUNT(*) FROM Reservation WHERE date = CURDATE() AND statut = 'CONFIRMEE'"));
            m.put("Salles (total)", scalar(st, "SELECT COUNT(*) FROM Salle"));
            m.put("Salles disponibles", scalar(st, "SELECT COUNT(*) FROM Salle WHERE disponible = TRUE"));
            m.put("Salles occupées aujourd'hui", scalar(st, "SELECT COUNT(DISTINCT idSalle) FROM Reservation WHERE date = CURDATE() AND statut = 'CONFIRMEE'"));
        }
        return m;
    }

    private int scalar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) { rs.next(); return rs.getInt(1); }
    }
}
