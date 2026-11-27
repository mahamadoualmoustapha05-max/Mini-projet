import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class DS1 {

    // =========================================================================
    // 1. MODÈLES SÉRIALISABLES & PROTOCOLE RÉSEAU
    // =========================================================================

    public static class Utilisateur implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public String nom, email, motDePasse, role;

        public Utilisateur(int id, String nom, String email, String motDePasse, String role) {
            this.id = id;
            this.nom = nom;
            this.email = email;
            this.motDePasse = motDePasse;
            this.role = role;
        }
    }

    public static class Salle implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, capacite;
        public String nom, type;
        public boolean disponible;

        public Salle(int id, String nom, String type, int capacite, boolean disponible) {
            this.id = id;
            this.nom = nom;
            this.type = type;
            this.capacite = capacite;
            this.disponible = disponible;
        }

        @Override
        public String toString() {
            return nom + " (" + type + " - " + capacite + " pl.)";
        }
    }

    public static class Reservation implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, idUtilisateur, idSalle;
        public String nomUtilisateur, nomSalle;
        public String date;
        public String heureDebut;
        public String heureFin;
        public String motif, statut;

        public Reservation(int id, int idUtilisateur, int idSalle, String date, String heureDebut, String heureFin, String motif, String statut) {
            this.id = id;
            this.idUtilisateur = idUtilisateur;
            this.idSalle = idSalle;
            this.date = date;
            this.heureDebut = heureDebut;
            this.heureFin = heureFin;
            this.motif = motif;
            this.statut = statut;
        }
    }

    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;
        public String action;
        public Object payload;
        public String userEmail;

        public Request(String action, Object payload, String userEmail) {
            this.action = action;
            this.payload = payload;
            this.userEmail = userEmail;
        }
    }

    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public String message;
        public Object data;

        public Response(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    // =========================================================================
    // 2. GESTION DES FICHIERS (LOGS, CSV, SÉRIALISATION BINAIRE)
    // =========================================================================

    public static class FileManager {
        private static final String LOG_FILE = "logs.txt";
        private static final String CSV_FILE = "reservations_export.csv";
        private static final String BIN_FILE = "reservations_backup.dat";

        public static synchronized void log(String user, String operation, String result) {
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8)))) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.printf("[%s] Utilisateur: %s | Opération: %s | Résultat: %s%n", timestamp, user, operation, result);
            } catch (IOException e) {
                System.err.println("Erreur d'écriture dans logs.txt : " + e.getMessage());
            }
        }

        public static void exportToCSV(List<Reservation> list, String filePath) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(filePath != null ? filePath : CSV_FILE), StandardCharsets.UTF_8))) {
                writer.write("ID;Utilisateur;Salle;Date;HeureDebut;HeureFin;Motif;Statut");
                writer.newLine();
                for (Reservation r : list) {
                    writer.write(String.join(";",
                            csv(r.id), csv(r.nomUtilisateur), csv(r.nomSalle), csv(r.date),
                            csv(r.heureDebut), csv(r.heureFin), csv(r.motif), csv(r.statut)));
                    writer.newLine();
                }
            }
        }

        private static String csv(Object value) {
            String text = value == null ? "" : value.toString();
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }

        public static void saveBinary(List<Reservation> list, String filePath) throws IOException {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath != null ? filePath : BIN_FILE))) {
                oos.writeObject(list);
            }
        }

        @SuppressWarnings("unchecked")
        public static List<Reservation> loadBinary(String filePath) throws IOException, ClassNotFoundException {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath != null ? filePath : BIN_FILE))) {
                return (List<Reservation>) ois.readObject();
            }
        }
    }

    // =========================================================================
    // 3. BASE DE DONNÉES & JDBC (AVEC SYNCHRONISATION ANTI-CONFLIT)
    // =========================================================================

    public static class DatabaseConnection {
        private static final String URL = System.getProperty("db.url",
                "jdbc:mysql://localhost:3306/ReservationDB?useSSL=false&serverTimezone=UTC");
        private static final String USER = System.getProperty("db.user", "root");
        private static final String PASSWORD = System.getProperty("db.password", "");

        public static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        }
    }

    public static class ReservationDAO {
        private static final Object RESERVATION_LOCK = new Object();

        public static Utilisateur authenticate(String email, String password) throws SQLException {
            String sql = "SELECT id, nom, email, role FROM Utilisateur WHERE email = ? AND motDePasse = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Utilisateur(
                                rs.getInt("id"),
                                rs.getString("nom"),
                                rs.getString("email"),
                                null,
                                rs.getString("role")
                        );
                    }
                }
            }
            return null;
        }

        public static List<Salle> getAllSalles() throws SQLException {
            List<Salle> list = new ArrayList<>();
            String sql = "SELECT * FROM Salle ORDER BY id";
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(new Salle(
                            rs.getInt("id"),
                            rs.getString("nom"),
                            rs.getString("type"),
                            rs.getInt("capacite"),
                            rs.getBoolean("disponible")
                    ));
                }
            }
            return list;
        }

        public static boolean addSalle(Salle s) throws SQLException {
            String sql = "INSERT INTO Salle (nom, type, capacite, disponible) VALUES (?, ?, ?, ?)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, s.nom);
                ps.setString(2, s.type);
                ps.setInt(3, s.capacite);
                ps.setBoolean(4, s.disponible);
                return ps.executeUpdate() > 0;
            }
        }

        public static boolean updateSalle(Salle s) throws SQLException {
            String sql = "UPDATE Salle SET nom=?, type=?, capacite=?, disponible=? WHERE id=?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, s.nom);
                ps.setString(2, s.type);
                ps.setInt(3, s.capacite);
                ps.setBoolean(4, s.disponible);
                ps.setInt(5, s.id);
                return ps.executeUpdate() > 0;
            }
        }

        public static boolean deleteSalle(int id) throws SQLException {
            String sql = "DELETE FROM Salle WHERE id=?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        }

        public static List<Reservation> getAllReservations(Integer userId) throws SQLException {
            List<Reservation> list = new ArrayList<>();
            String sql = "SELECT r.*, u.nom AS nomUser, s.nom AS nomSalle " +
                         "FROM Reservation r " +
                         "JOIN Utilisateur u ON r.idUtilisateur = u.id " +
                         "JOIN Salle s ON r.idSalle = s.id " +
                         (userId == null ? "" : "WHERE r.idUtilisateur = ? ") +
                         "ORDER BY r.date DESC, r.heureDebut ASC";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement st = conn.prepareStatement(sql)) {
                if (userId != null) st.setInt(1, userId);
                try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Reservation res = new Reservation(
                            rs.getInt("id"),
                            rs.getInt("idUtilisateur"),
                            rs.getInt("idSalle"),
                            rs.getString("date"),
                            rs.getString("heureDebut"),
                            rs.getString("heureFin"),
                            rs.getString("motif"),
                            rs.getString("statut")
                    );
                    res.nomUtilisateur = rs.getString("nomUser");
                    res.nomSalle = rs.getString("nomSalle");
                    list.add(res);
                }
                }
            }
            return list;
        }

        public static Integer getReservationOwnerId(int id) throws SQLException {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT idUtilisateur FROM Reservation WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Integer.valueOf(rs.getInt(1)) : null;
                }
            }
        }

        public static Response addReservation(Reservation r) throws SQLException {
            synchronized (RESERVATION_LOCK) {
                Connection conn = null;
                try {
                    conn = DatabaseConnection.getConnection();
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

                    String checkSalleSql = "SELECT disponible FROM Salle WHERE id = ? FOR UPDATE";
                    try (PreparedStatement psSalle = conn.prepareStatement(checkSalleSql)) {
                        psSalle.setInt(1, r.idSalle);
                        ResultSet rsSalle = psSalle.executeQuery();
                        if (!rsSalle.next() || !rsSalle.getBoolean("disponible")) {
                            conn.rollback();
                            return new Response(false, "Cette salle est actuellement indisponible ou fermée.", null);
                        }
                    }

                    String overlapSql = "SELECT COUNT(*) FROM Reservation " +
                                        "WHERE idSalle = ? AND date = ? AND statut != 'ANNULEE' " +
                                        "AND (heureDebut < ? AND heureFin > ?) FOR UPDATE";
                    try (PreparedStatement psCheck = conn.prepareStatement(overlapSql)) {
                        psCheck.setInt(1, r.idSalle);
                        psCheck.setDate(2, java.sql.Date.valueOf(r.date));
                        psCheck.setTime(3, Time.valueOf(r.heureFin));
                        psCheck.setTime(4, Time.valueOf(r.heureDebut));
                        ResultSet rs = psCheck.executeQuery();
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return new Response(false, "Conflit détecté : La salle est déjà réservée sur ce créneau horaire.", null);
                        }
                    }

                    String insertSql = "INSERT INTO Reservation (idUtilisateur, idSalle, date, heureDebut, heureFin, motif, statut) " +
                                       "VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMEE')";
                    int id;
                    try (PreparedStatement psInsert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        psInsert.setInt(1, r.idUtilisateur);
                        psInsert.setInt(2, r.idSalle);
                        psInsert.setDate(3, java.sql.Date.valueOf(r.date));
                        psInsert.setTime(4, Time.valueOf(r.heureDebut));
                        psInsert.setTime(5, Time.valueOf(r.heureFin));
                        psInsert.setString(6, r.motif);
                        psInsert.executeUpdate();
                        try (ResultSet keys = psInsert.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("L'identifiant de la réservation n'a pas été généré.");
                            id = keys.getInt(1);
                        }
                    }

                    conn.commit();
                    return new Response(true, "Réservation confirmée avec succès.", Integer.valueOf(id));
                } catch (Exception e) {
                    if (conn != null) conn.rollback();
                    return new Response(false, "Erreur SQL : " + e.getMessage(), null);
                } finally {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                }
            }
        }

        public static Response updateReservation(Reservation r) throws SQLException {
            synchronized (RESERVATION_LOCK) {
                Connection conn = null;
                try {
                    conn = DatabaseConnection.getConnection();
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

                    String checkSalleSql = "SELECT disponible FROM Salle WHERE id = ? FOR UPDATE";
                    try (PreparedStatement psSalle = conn.prepareStatement(checkSalleSql)) {
                        psSalle.setInt(1, r.idSalle);
                        try (ResultSet rsSalle = psSalle.executeQuery()) {
                            if (!rsSalle.next() || !rsSalle.getBoolean("disponible")) {
                                conn.rollback();
                                return new Response(false, "Cette salle est introuvable ou indisponible.", null);
                            }
                        }
                    }

                    String overlapSql = "SELECT COUNT(*) FROM Reservation " +
                                        "WHERE idSalle = ? AND date = ? AND statut != 'ANNULEE' AND id != ? " +
                                        "AND (heureDebut < ? AND heureFin > ?) FOR UPDATE";
                    try (PreparedStatement psCheck = conn.prepareStatement(overlapSql)) {
                        psCheck.setInt(1, r.idSalle);
                        psCheck.setDate(2, java.sql.Date.valueOf(r.date));
                        psCheck.setInt(3, r.id);
                        psCheck.setTime(4, Time.valueOf(r.heureFin));
                        psCheck.setTime(5, Time.valueOf(r.heureDebut));
                        ResultSet rs = psCheck.executeQuery();
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return new Response(false, "Modification impossible : Créneau déjà occupé.", null);
                        }
                    }

                    String sql = "UPDATE Reservation SET idSalle=?, date=?, heureDebut=?, heureFin=?, motif=? WHERE id=?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, r.idSalle);
                        ps.setDate(2, java.sql.Date.valueOf(r.date));
                        ps.setTime(3, Time.valueOf(r.heureDebut));
                        ps.setTime(4, Time.valueOf(r.heureFin));
                        ps.setString(5, r.motif);
                        ps.setInt(6, r.id);
                        if (ps.executeUpdate() == 0) {
                            conn.rollback();
                            return new Response(false, "Réservation introuvable.", null);
                        }
                    }

                    conn.commit();
                    return new Response(true, "Réservation mise à jour.", null);
                } catch (Exception e) {
                    if (conn != null) conn.rollback();
                    return new Response(false, "Erreur modification : " + e.getMessage(), null);
                } finally {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                }
            }
        }

        public static boolean cancelReservation(int id) throws SQLException {
            String sql = "UPDATE Reservation SET statut = 'ANNULEE' WHERE id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        }

        public static boolean deleteReservation(int id) throws SQLException {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM Reservation WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        }

        public static Map<String, Integer> getStats() throws SQLException {
            Map<String, Integer> stats = new HashMap<>();
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement()) {
                ResultSet rs1 = st.executeQuery("SELECT COUNT(*) FROM Salle");
                if (rs1.next()) stats.put("totalSalles", rs1.getInt(1));

                ResultSet rs2 = st.executeQuery("SELECT COUNT(*) FROM Salle WHERE disponible = TRUE");
                if (rs2.next()) stats.put("sallesDispo", rs2.getInt(1));

                ResultSet rs3 = st.executeQuery("SELECT COUNT(*) FROM Reservation WHERE statut = 'CONFIRMEE'");
                if (rs3.next()) stats.put("resConfirmees", rs3.getInt(1));

                ResultSet rs4 = st.executeQuery("SELECT COUNT(*) FROM Reservation WHERE statut = 'ANNULEE'");
                if (rs4.next()) stats.put("resAnnulees", rs4.getInt(1));
            }
            return stats;
        }
    }

    // =========================================================================
    // 4. SERVEUR TCP MULTITHREAD (port 5000)
    // =========================================================================

    public static class ReservationServer {
        private static final int PORT = 5000;

        public static void startServer() {
            System.out.println("=== Démarrage de ReservationServer sur le port " + PORT + " ===");
            FileManager.log("SYSTEM", "SERVER_START", "Port " + PORT);
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());
                    Thread clientThread = new Thread(new ClientHandler(clientSocket));
                    clientThread.start();
                }
            } catch (IOException e) {
                System.err.println("Erreur serveur : " + e.getMessage());
            }
        }
    }

    public static class ClientHandler implements Runnable {
        private final Socket socket;
        private Utilisateur currentUser;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                while (true) {
                    Request req = (Request) in.readObject();
                    Response resp = processRequest(req);
                    out.writeObject(resp);
                    out.flush();
                }
            } catch (EOFException | SocketException e) {
                System.out.println("Client déconnecté : " + socket.getInetAddress());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Response processRequest(Request req) {
            String user = currentUser == null ? "Anonyme" : currentUser.email;
            try {
                if (req == null || req.action == null) {
                    return new Response(false, "Requête invalide.", null);
                }
                switch (req.action) {
                    case "LOGIN": {
                        if (currentUser != null) {
                            return new Response(false, "Une session est déjà ouverte sur cette connexion.", null);
                        }
                        if (!(req.payload instanceof String[]) || ((String[]) req.payload).length != 2) {
                            return new Response(false, "Identifiants invalides.", null);
                        }
                        String[] creds = (String[]) req.payload;
                        Utilisateur u = ReservationDAO.authenticate(creds[0], creds[1]);
                        if (u != null) {
                            currentUser = u;
                            FileManager.log(u.email, "AUTHENTIFICATION", "SUCCÈS");
                            return new Response(true, "Bienvenue " + u.nom, u);
                        } else {
                            FileManager.log(req.userEmail == null ? "Anonyme" : req.userEmail, "AUTHENTIFICATION", "ÉCHEC");
                            return new Response(false, "Email ou mot de passe incorrect.", null);
                        }
                    }
                    default:
                        break;
                }

                if (currentUser == null) {
                    FileManager.log(user, req.action, "REFUSÉ - authentification requise");
                    return new Response(false, "Authentification requise.", null);
                }

                switch (req.action) {
                    case "GET_SALLES": {
                        List<Salle> salles = ReservationDAO.getAllSalles();
                        FileManager.log(user, "CONSULTATION_SALLES", "SUCCÈS");
                        return new Response(true, "Liste des salles", salles);
                    }
                    case "ADD_SALLE": {
                        if (!isAdmin()) return new Response(false, "Action réservée à l'administrateur.", null);
                        Salle salle = (Salle) req.payload;
                        String validationError = validateSalle(salle);
                        if (validationError != null) return new Response(false, validationError, null);
                        boolean ok = ReservationDAO.addSalle(salle);
                        FileManager.log(user, "AJOUT_SALLE", ok ? "SUCCÈS" : "ÉCHEC");
                        return new Response(ok, ok ? "Salle ajoutée." : "Erreur d'ajout.", null);
                    }
                    case "UPDATE_SALLE": {
                        if (!isAdmin()) return new Response(false, "Action réservée à l'administrateur.", null);
                        Salle salle = (Salle) req.payload;
                        String validationError = validateSalle(salle);
                        if (validationError != null) return new Response(false, validationError, null);
                        boolean ok = ReservationDAO.updateSalle(salle);
                        FileManager.log(user, "MODIF_SALLE", ok ? "SUCCÈS" : "ÉCHEC");
                        return new Response(ok, ok ? "Salle modifiée." : "Erreur de modification.", null);
                    }
                    case "DELETE_SALLE": {
                        if (!isAdmin()) return new Response(false, "Action réservée à l'administrateur.", null);
                        boolean ok = ReservationDAO.deleteSalle((Integer) req.payload);
                        FileManager.log(user, "SUPPR_SALLE", ok ? "SUCCÈS" : "ÉCHEC");
                        return new Response(ok, ok ? "Salle supprimée." : "Erreur de suppression.", null);
                    }
                    case "GET_RESERVATIONS": {
                        Integer userId = isAdmin() ? null : Integer.valueOf(currentUser.id);
                        List<Reservation> res = ReservationDAO.getAllReservations(userId);
                        FileManager.log(user, "CONSULTATION_RESERVATIONS", "SUCCÈS");
                        return new Response(true, "Liste des réservations", res);
                    }
                    case "ADD_RESERVATION": {
                        Reservation reservation = (Reservation) req.payload;
                        String validationError = validateReservation(reservation);
                        if (validationError != null) return new Response(false, validationError, null);
                        reservation.idUtilisateur = currentUser.id;
                        Response r = ReservationDAO.addReservation(reservation);
                        FileManager.log(user, "AJOUT_RESERVATION", r.success ? "SUCCÈS" : "CONFLIT/ÉCHEC: " + r.message);
                        return r;
                    }
                    case "UPDATE_RESERVATION": {
                        Reservation reservation = (Reservation) req.payload;
                        Response access = checkReservationAccess(reservation.id);
                        if (access != null) return access;
                        String validationError = validateReservation(reservation);
                        if (validationError != null) return new Response(false, validationError, null);
                        Response r = ReservationDAO.updateReservation(reservation);
                        FileManager.log(user, "MODIF_RESERVATION", r.success ? "SUCCÈS" : "ÉCHEC: " + r.message);
                        return r;
                    }
                    case "CANCEL_RESERVATION": {
                        int id = (Integer) req.payload;
                        Response access = checkReservationAccess(id);
                        if (access != null) return access;
                        boolean ok = ReservationDAO.cancelReservation(id);
                        FileManager.log(user, "ANNULATION_RESERVATION", ok ? "SUCCÈS" : "ÉCHEC");
                        return new Response(ok, ok ? "Réservation annulée." : "Erreur d'annulation.", null);
                    }
                    case "DELETE_RESERVATION": {
                        int id = (Integer) req.payload;
                        Response access = checkReservationAccess(id);
                        if (access != null) return access;
                        boolean ok = ReservationDAO.deleteReservation(id);
                        FileManager.log(user, "SUPPRESSION_RESERVATION", ok ? "SUCCÈS" : "ÉCHEC");
                        return new Response(ok, ok ? "Réservation supprimée." : "Réservation introuvable.", null);
                    }
                    case "GET_STATS": {
                        Map<String, Integer> stats = ReservationDAO.getStats();
                        FileManager.log(user, "CONSULTATION_STATISTIQUES", "SUCCÈS");
                        return new Response(true, "Statistiques", stats);
                    }
                    default:
                        return new Response(false, "Action inconnue : " + req.action, null);
                }
            } catch (SQLException e) {
                FileManager.log(user, req.action, "ERREUR_SQL: " + e.getMessage());
                return new Response(false, "Erreur base de données : " + e.getMessage(), null);
            } catch (RuntimeException e) {
                FileManager.log(user, req.action, "REQUÊTE INVALIDE: " + e.getMessage());
                return new Response(false, "Requête invalide.", null);
            }
        }

        private boolean isAdmin() {
            return "ADMIN".equalsIgnoreCase(currentUser.role);
        }

        private Response checkReservationAccess(int reservationId) throws SQLException {
            Integer ownerId = ReservationDAO.getReservationOwnerId(reservationId);
            if (ownerId == null) return new Response(false, "Réservation introuvable.", null);
            if (!isAdmin() && ownerId.intValue() != currentUser.id) {
                return new Response(false, "Accès refusé : cette réservation ne vous appartient pas.", null);
            }
            return null;
        }

        private String validateReservation(Reservation reservation) {
            if (reservation == null || reservation.idSalle <= 0) return "Choisissez une salle valide.";
            if (reservation.date == null || reservation.heureDebut == null || reservation.heureFin == null) {
                return "La date et les heures sont obligatoires.";
            }
            if (reservation.motif == null || reservation.motif.trim().isEmpty()) return "Le motif est obligatoire.";
            try {
                if (LocalDate.parse(reservation.date).isBefore(LocalDate.now())) {
                    return "La date ne peut pas être dans le passé.";
                }
                LocalTime debut = LocalTime.parse(reservation.heureDebut);
                LocalTime fin = LocalTime.parse(reservation.heureFin);
                if (!fin.isAfter(debut)) return "L'heure de fin doit être après l'heure de début.";
            } catch (RuntimeException e) {
                return "Format invalide : date AAAA-MM-JJ et heures HH:MM ou HH:MM:SS.";
            }
            return null;
        }

        private String validateSalle(Salle salle) {
            if (salle == null || salle.nom == null || salle.nom.trim().isEmpty()) return "Le nom de la salle est obligatoire.";
            if (salle.type == null || salle.type.trim().isEmpty()) return "Le type de la salle est obligatoire.";
            if (salle.capacite <= 0) return "La capacité doit être positive.";
            return null;
        }
    }

    // =========================================================================
    // 5. CLIENT RÉSEAU (SOCKET TCP)
    // =========================================================================

    public static class ReservationClient {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private final String host;
        private final int port;

        public ReservationClient(String host, int port) throws IOException {
            this.host = host;
            this.port = port;
            connect();
        }

        public synchronized void connect() throws IOException {
            close();
            try {
                this.socket = new Socket();
                this.socket.connect(new InetSocketAddress(host, port), 3000);
                this.socket.setSoTimeout(15000);
                this.out = new ObjectOutputStream(socket.getOutputStream());
                this.out.flush();
                this.in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                close();
                throw e;
            }
        }

        public synchronized Response sendRequest(Request req) throws IOException, ClassNotFoundException {
            if (socket == null || socket.isClosed()) {
                connect();
            }
            out.writeObject(req);
            out.flush();
            out.reset();
            return (Response) in.readObject();
        }

        public void close() {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }

    // =========================================================================
    // 6. INTERFACE GRAPHIQUE SWING (AVEC SWINGWORKER)
    // =========================================================================

    public static class MainAppUI extends JFrame {
        private ReservationClient client;
        private Utilisateur currentUser;

        private JLabel lblTotalSalles, lblSallesDispo, lblResConfirmees, lblResAnnulees;

        private JTable tableReservations;
        private DefaultTableModel modelReservations;
        private JTextField txtSearchRes, txtDate, txtHeureDebut, txtHeureFin, txtMotif;
        private JComboBox<Salle> comboSalles;
        private List<Reservation> cachedReservations = new ArrayList<>();

        private JTable tableSalles;
        private DefaultTableModel modelSalles;
        private JTextField txtSalleNom, txtSalleType, txtSalleCapacite;
        private JCheckBox chkSalleDispo;

        public MainAppUI(ReservationClient client, Utilisateur user) {
            this.client = client;
            this.currentUser = user;

            setTitle("Gestion des Réservations de Salles - Connecté : " + user.nom + " [" + user.role + "]");
            setSize(1050, 680);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.addTab("Tableau de Bord", createDashboardPanel());
            tabbedPane.addTab("Gestion des Réservations", createReservationPanel());

            if ("ADMIN".equalsIgnoreCase(user.role)) {
                tabbedPane.addTab("Administration des Salles", createAdminSallePanel());
            }

            add(tabbedPane, BorderLayout.CENTER);
            refreshAllData();
        }

        private JPanel createDashboardPanel() {
            JPanel panel = new JPanel(new BorderLayout(15, 15));
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel title = new JLabel("Statistiques en temps réel", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 22));
            panel.add(title, BorderLayout.NORTH);

            JPanel cardsPanel = new JPanel(new GridLayout(2, 2, 20, 20));
            lblTotalSalles = createStatCard(cardsPanel, "Total des Salles", "0", new Color(52, 152, 219));
            lblSallesDispo = createStatCard(cardsPanel, "Salles Disponibles", "0", new Color(46, 204, 113));
            lblResConfirmees = createStatCard(cardsPanel, "Réservations Confirmées", "0", new Color(155, 89, 182));
            lblResAnnulees = createStatCard(cardsPanel, "Réservations Annulées", "0", new Color(231, 76, 60));

            panel.add(cardsPanel, BorderLayout.CENTER);

            JButton btnRefresh = new JButton("Actualiser les données");
            btnRefresh.addActionListener(e -> refreshAllData());
            panel.add(btnRefresh, BorderLayout.SOUTH);

            return panel;
        }

        private JLabel createStatCard(JPanel parent, String title, String value, Color color) {
            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(color);
            card.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
            lblTitle.setForeground(Color.WHITE);
            lblTitle.setFont(new Font("SansSerif", Font.BOLD, 16));

            JLabel lblVal = new JLabel(value, SwingConstants.CENTER);
            lblVal.setForeground(Color.WHITE);
            lblVal.setFont(new Font("SansSerif", Font.BOLD, 36));

            card.add(lblTitle, BorderLayout.NORTH);
            card.add(lblVal, BorderLayout.CENTER);
            parent.add(card);
            return lblVal;
        }

        private JPanel createReservationPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            topBar.add(new JLabel("Rechercher (Salle/Motif/Date) :"));
            txtSearchRes = new JTextField(18);
            JButton btnSearch = new JButton("Filtrer");
            btnSearch.addActionListener(e -> filterReservations());

            JButton btnExportCSV = new JButton("Exporter CSV");
            btnExportCSV.addActionListener(e -> exportCSVAction());

            JButton btnSaveBin = new JButton("Sauvegarder Binaire");
            btnSaveBin.addActionListener(e -> saveBinaryAction());

            JButton btnLoadBin = new JButton("Lire Binaire");
            btnLoadBin.addActionListener(e -> loadBinaryAction());

            topBar.add(txtSearchRes);
            topBar.add(btnSearch);
            topBar.add(btnExportCSV);
            topBar.add(btnSaveBin);
            topBar.add(btnLoadBin);
            panel.add(topBar, BorderLayout.NORTH);

            String[] cols = {"ID", "Utilisateur", "Salle", "Date", "Début", "Fin", "Motif", "Statut"};
            modelReservations = new DefaultTableModel(cols, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            tableReservations = new JTable(modelReservations);
            tableReservations.getSelectionModel().addListSelectionListener(e -> fillReservationFormFromSelection());
            panel.add(new JScrollPane(tableReservations), BorderLayout.CENTER);

            JPanel formPanel = new JPanel(new GridLayout(3, 4, 8, 8));
            formPanel.setBorder(BorderFactory.createTitledBorder("Nouvelle Réservation / Modification"));

            comboSalles = new JComboBox<>();
            txtDate = new JTextField(LocalDate.now().plusDays(1).toString());
            txtHeureDebut = new JTextField("09:00:00");
            txtHeureFin = new JTextField("11:00:00");
            txtMotif = new JTextField("Cours / Réunion");

            formPanel.add(new JLabel("Salle :"));
            formPanel.add(comboSalles);
            formPanel.add(new JLabel("Date (YYYY-MM-DD) :"));
            formPanel.add(txtDate);
            formPanel.add(new JLabel("Heure Début (HH:MM:SS) :"));
            formPanel.add(txtHeureDebut);
            formPanel.add(new JLabel("Heure Fin (HH:MM:SS) :"));
            formPanel.add(txtHeureFin);
            formPanel.add(new JLabel("Motif :"));
            formPanel.add(txtMotif);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnAdd = new JButton("Réserver");
            JButton btnUpdate = new JButton("Modifier");
            JButton btnCancel = new JButton("Annuler Réservation");
            JButton btnDelete = new JButton("Supprimer");

            btnAdd.addActionListener(e -> handleReservationCRUD("ADD_RESERVATION"));
            btnUpdate.addActionListener(e -> handleReservationCRUD("UPDATE_RESERVATION"));
            btnCancel.addActionListener(e -> handleCancelReservation());
            btnDelete.addActionListener(e -> handleDeleteReservation());

            btnPanel.add(btnAdd);
            btnPanel.add(btnUpdate);
            btnPanel.add(btnCancel);
            btnPanel.add(btnDelete);

            JPanel southContainer = new JPanel(new BorderLayout());
            southContainer.add(formPanel, BorderLayout.CENTER);
            southContainer.add(btnPanel, BorderLayout.SOUTH);
            panel.add(southContainer, BorderLayout.SOUTH);

            return panel;
        }

        private JPanel createAdminSallePanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            String[] cols = {"ID", "Nom", "Type", "Capacité", "Disponible"};
            modelSalles = new DefaultTableModel(cols, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            tableSalles = new JTable(modelSalles);
            tableSalles.getSelectionModel().addListSelectionListener(e -> {
                int row = tableSalles.getSelectedRow();
                if (row >= 0) {
                    txtSalleNom.setText(modelSalles.getValueAt(row, 1).toString());
                    txtSalleType.setText(modelSalles.getValueAt(row, 2).toString());
                    txtSalleCapacite.setText(modelSalles.getValueAt(row, 3).toString());
                    chkSalleDispo.setSelected((Boolean) modelSalles.getValueAt(row, 4));
                }
            });
            panel.add(new JScrollPane(tableSalles), BorderLayout.CENTER);

            JPanel form = new JPanel(new GridLayout(2, 4, 8, 8));
            form.setBorder(BorderFactory.createTitledBorder("Gestion des Salles (Admin)"));
            txtSalleNom = new JTextField();
            txtSalleType = new JTextField();
            txtSalleCapacite = new JTextField();
            chkSalleDispo = new JCheckBox("Disponible", true);

            form.add(new JLabel("Nom :"));
            form.add(txtSalleNom);
            form.add(new JLabel("Type :"));
            form.add(txtSalleType);
            form.add(new JLabel("Capacité :"));
            form.add(txtSalleCapacite);
            form.add(new JLabel("État :"));
            form.add(chkSalleDispo);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnAdd = new JButton("Ajouter Salle");
            JButton btnEdit = new JButton("Modifier Salle");
            JButton btnDel = new JButton("Supprimer Salle");

            btnAdd.addActionListener(e -> handleSalleCRUD("ADD_SALLE"));
            btnEdit.addActionListener(e -> handleSalleCRUD("UPDATE_SALLE"));
            btnDel.addActionListener(e -> handleSalleCRUD("DELETE_SALLE"));

            btns.add(btnAdd);
            btns.add(btnEdit);
            btns.add(btnDel);

            JPanel south = new JPanel(new BorderLayout());
            south.add(form, BorderLayout.CENTER);
            south.add(btns, BorderLayout.SOUTH);
            panel.add(south, BorderLayout.SOUTH);

            return panel;
        }

        private void executeAsync(Request req, java.util.function.Consumer<Response> onSuccess) {
            new SwingWorker<Response, Void>() {
                @Override
                protected Response doInBackground() throws Exception {
                    return client.sendRequest(req);
                }

                @Override
                protected void done() {
                    try {
                        Response resp = get();
                        onSuccess.accept(resp);
                    } catch (Exception ex) {
                        handleConnectionError(ex);
                    }
                }
            }.execute();
        }

        private void handleConnectionError(Exception ex) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Perte de connexion avec le serveur (" + ex.getMessage() + ").\nVoulez-vous retourner à l'authentification ?",
                    "Erreur Réseau", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                client.close();
                dispose();
                new LoginFrame(currentUser.email).setVisible(true);
            }
        }

        @SuppressWarnings("unchecked")
        private void refreshAllData() {
            executeAsync(new Request("GET_SALLES", null, currentUser.email), resp -> {
                if (resp.success) {
                    List<Salle> salles = (List<Salle>) resp.data;
                    comboSalles.removeAllItems();
                    if (modelSalles != null) modelSalles.setRowCount(0);
                    for (Salle s : salles) {
                        comboSalles.addItem(s);
                        if (modelSalles != null) {
                            modelSalles.addRow(new Object[]{s.id, s.nom, s.type, s.capacite, s.disponible});
                        }
                    }
                }
            });

            executeAsync(new Request("GET_RESERVATIONS", null, currentUser.email), resp -> {
                if (resp.success) {
                    cachedReservations = (List<Reservation>) resp.data;
                    updateReservationTable(cachedReservations);
                }
            });

            executeAsync(new Request("GET_STATS", null, currentUser.email), resp -> {
                if (resp.success) {
                    Map<String, Integer> stats = (Map<String, Integer>) resp.data;
                    lblTotalSalles.setText(String.valueOf(stats.getOrDefault("totalSalles", 0)));
                    lblSallesDispo.setText(String.valueOf(stats.getOrDefault("sallesDispo", 0)));
                    lblResConfirmees.setText(String.valueOf(stats.getOrDefault("resConfirmees", 0)));
                    lblResAnnulees.setText(String.valueOf(stats.getOrDefault("resAnnulees", 0)));
                }
            });
        }

        private void updateReservationTable(List<Reservation> list) {
            modelReservations.setRowCount(0);
            for (Reservation r : list) {
                modelReservations.addRow(new Object[]{
                        r.id, r.nomUtilisateur, r.nomSalle, r.date, r.heureDebut, r.heureFin, r.motif, r.statut
                });
            }
        }

        private void filterReservations() {
            String q = txtSearchRes.getText().trim().toLowerCase();
            if (q.isEmpty()) {
                updateReservationTable(cachedReservations);
                return;
            }
            List<Reservation> filtered = new ArrayList<>();
            for (Reservation r : cachedReservations) {
                if (r.nomSalle.toLowerCase().contains(q) ||
                    r.motif.toLowerCase().contains(q) ||
                    r.date.contains(q) ||
                    r.nomUtilisateur.toLowerCase().contains(q)) {
                    filtered.add(r);
                }
            }
            updateReservationTable(filtered);
        }

        private void fillReservationFormFromSelection() {
            int row = tableReservations.getSelectedRow();
            if (row >= 0) {
                txtDate.setText(modelReservations.getValueAt(row, 3).toString());
                txtHeureDebut.setText(modelReservations.getValueAt(row, 4).toString());
                txtHeureFin.setText(modelReservations.getValueAt(row, 5).toString());
                txtMotif.setText(modelReservations.getValueAt(row, 6).toString());
            }
        }

        private void handleReservationCRUD(String action) {
            Salle selectedSalle = (Salle) comboSalles.getSelectedItem();
            if (selectedSalle == null) return;

            try {
                LocalDate.parse(txtDate.getText().trim());
                LocalTime d = LocalTime.parse(txtHeureDebut.getText().trim());
                LocalTime f = LocalTime.parse(txtHeureFin.getText().trim());
                if (!f.isAfter(d)) {
                    JOptionPane.showMessageDialog(this, "L'heure de fin doit être après l'heure de début.");
                    return;
                }

                int resId = 0;
                if ("UPDATE_RESERVATION".equals(action)) {
                    int row = tableReservations.getSelectedRow();
                    if (row < 0) {
                        JOptionPane.showMessageDialog(this, "Sélectionnez une réservation à modifier.");
                        return;
                    }
                    resId = (Integer) modelReservations.getValueAt(row, 0);
                }

                Reservation r = new Reservation(
                        resId,
                        currentUser.id,
                        selectedSalle.id,
                        txtDate.getText().trim(),
                        txtHeureDebut.getText().trim(),
                        txtHeureFin.getText().trim(),
                        txtMotif.getText().trim(),
                        "CONFIRMEE"
                );

                executeAsync(new Request(action, r, currentUser.email), resp -> {
                    JOptionPane.showMessageDialog(this, resp.message,
                            resp.success ? "Succès" : "Indisponibilité / Erreur",
                            resp.success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    if (resp.success) refreshAllData();
                });

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Format de date (YYYY-MM-DD) ou d'heure (HH:MM:SS) invalide.");
            }
        }

        private void handleCancelReservation() {
            int row = tableReservations.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Veuillez sélectionner une réservation à annuler.");
                return;
            }
            int id = (Integer) modelReservations.getValueAt(row, 0);
            executeAsync(new Request("CANCEL_RESERVATION", id, currentUser.email), resp -> {
                JOptionPane.showMessageDialog(this, resp.message);
                if (resp.success) refreshAllData();
            });
        }

        private void handleDeleteReservation() {
            int row = tableReservations.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Veuillez sélectionner une réservation à supprimer.");
                return;
            }
            int id = (Integer) modelReservations.getValueAt(row, 0);
            int choice = JOptionPane.showConfirmDialog(this,
                    "Supprimer définitivement cette réservation ?", "Confirmation",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
            executeAsync(new Request("DELETE_RESERVATION", id, currentUser.email), resp -> {
                JOptionPane.showMessageDialog(this, resp.message);
                if (resp.success) refreshAllData();
            });
        }

        private void handleSalleCRUD(String action) {
            try {
                if ("DELETE_SALLE".equals(action)) {
                    int row = tableSalles.getSelectedRow();
                    if (row < 0) return;
                    int id = (Integer) modelSalles.getValueAt(row, 0);
                    executeAsync(new Request("DELETE_SALLE", id, currentUser.email), resp -> {
                        JOptionPane.showMessageDialog(this, resp.message);
                        refreshAllData();
                    });
                    return;
                }

                int id = 0;
                if ("UPDATE_SALLE".equals(action)) {
                    int row = tableSalles.getSelectedRow();
                    if (row < 0) return;
                    id = (Integer) modelSalles.getValueAt(row, 0);
                }

                Salle s = new Salle(
                        id,
                        txtSalleNom.getText().trim(),
                        txtSalleType.getText().trim(),
                        Integer.parseInt(txtSalleCapacite.getText().trim()),
                        chkSalleDispo.isSelected()
                );

                executeAsync(new Request(action, s, currentUser.email), resp -> {
                    JOptionPane.showMessageDialog(this, resp.message);
                    refreshAllData();
                });
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Capacité invalide.");
            }
        }

        private void exportCSVAction() {
            List<Reservation> snapshot = new ArrayList<>(cachedReservations);
            executeFileAsync(() -> {
                FileManager.exportToCSV(snapshot, "reservations_export.csv");
                return null;
            }, ignored -> JOptionPane.showMessageDialog(this, "Exportation réussie vers 'reservations_export.csv'."));
        }

        private void saveBinaryAction() {
            List<Reservation> snapshot = new ArrayList<>(cachedReservations);
            executeFileAsync(() -> {
                FileManager.saveBinary(snapshot, "reservations_backup.dat");
                return null;
            }, ignored -> JOptionPane.showMessageDialog(this, "Sauvegarde binaire effectuée dans 'reservations_backup.dat'."));
        }

        private void loadBinaryAction() {
            executeFileAsync(() -> FileManager.loadBinary("reservations_backup.dat"), loaded -> {
                StringBuilder sb = new StringBuilder("Contenu du fichier binaire (" + loaded.size() + " réservations) :\n\n");
                for (Reservation r : loaded) {
                    sb.append(String.format("#%d | Salle: %s | Date: %s (%s - %s) | Statut: %s\n",
                            r.id, r.nomSalle, r.date, r.heureDebut, r.heureFin, r.statut));
                }
                JTextArea area = new JTextArea(sb.toString(), 15, 50);
                area.setEditable(false);
                JOptionPane.showMessageDialog(this, new JScrollPane(area), "Lecture Fichier Binaire", JOptionPane.INFORMATION_MESSAGE);
            });
        }

        private <T> void executeFileAsync(java.util.concurrent.Callable<T> task,
                                          java.util.function.Consumer<T> onSuccess) {
            new SwingWorker<T, Void>() {
                @Override
                protected T doInBackground() throws Exception {
                    return task.call();
                }

                @Override
                protected void done() {
                    try {
                        onSuccess.accept(get());
                    } catch (Exception e) {
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        JOptionPane.showMessageDialog(MainAppUI.this, cause.getMessage(),
                                "Erreur fichier", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    // =========================================================================
    // 7. FENÊTRE D'AUTHENTIFICATION (LOGIN)
    // =========================================================================

    public static class LoginFrame extends JFrame {
        private JTextField txtEmail;
        private JPasswordField txtPassword;
        private JButton btnLogin;

        public LoginFrame() {
            this("ahmed@ecole.ma");
        }

        public LoginFrame(String initialEmail) {
            setTitle("Authentification - Réservation de Salles");
            setSize(380, 220);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));

            panel.add(new JLabel("Email :"));
            txtEmail = new JTextField(initialEmail);
            panel.add(txtEmail);

            panel.add(new JLabel("Mot de passe :"));
            txtPassword = new JPasswordField("1234");
            panel.add(txtPassword);

            panel.add(new JLabel(""));
            btnLogin = new JButton("Se connecter");
            panel.add(btnLogin);

            add(panel);

            btnLogin.addActionListener(e -> performLogin());
        }

        private void performLogin() {
            String email = txtEmail.getText().trim();
            String pass = new String(txtPassword.getPassword());
            btnLogin.setEnabled(false);

            new SwingWorker<Response, Void>() {
                ReservationClient client;

                @Override
                protected Response doInBackground() throws Exception {
                    client = new ReservationClient("localhost", 5000);
                    return client.sendRequest(new Request("LOGIN", new String[]{email, pass}, email));
                }

                @Override
                protected void done() {
                    btnLogin.setEnabled(true);
                    try {
                        Response resp = get();
                        if (resp.success) {
                            Utilisateur user = (Utilisateur) resp.data;
                            dispose();
                            new MainAppUI(client, user).setVisible(true);
                        } else {
                            client.close();
                            JOptionPane.showMessageDialog(LoginFrame.this, resp.message, "Erreur", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        if (client != null) client.close();
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "Impossible de joindre le serveur (Port 5000) : " + ex.getMessage(),
                                "Erreur Réseau", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    public static class IntegrationTest {
        private IntegrationTest() { }

        public static void run(boolean localOnly) throws Exception {
            testFiles();
            testConnectionLoss();
            if (localOnly) {
                System.out.println("DS1 : tests locaux réussis.");
                return;
            }

            Thread server = new Thread(ReservationServer::startServer, "ds1-test-server");
            server.setDaemon(true);
            server.start();
            Thread.sleep(700);

            ReservationClient anonymous = null;
            ReservationClient admin = null;
            ReservationClient user1 = null;
            ReservationClient user2 = null;
            String roomName = "DS1_TEST_" + System.currentTimeMillis();
            int roomId = -1;

            try {
                anonymous = connect();
                Response unauthenticated = anonymous.sendRequest(new Request("GET_RESERVATIONS", null, null));
                check(!unauthenticated.success, "une action doit être refusée avant l'authentification");

                admin = login("admin@ecole.ma", "admin123");
                user1 = login("ahmed@ecole.ma", "1234");
                user2 = login("sara@ecole.ma", "1234");

                Response forbiddenRoomWrite = user1.sendRequest(new Request("ADD_SALLE",
                        new Salle(0, roomName + "_FORBIDDEN", "Test", 10, true), null));
                check(!forbiddenRoomWrite.success, "un utilisateur ne doit pas pouvoir administrer les salles");

                Response addedRoom = admin.sendRequest(new Request("ADD_SALLE",
                        new Salle(0, roomName, "Test", 10, true), null));
                check(addedRoom.success, "l'administrateur doit pouvoir créer une salle");

                @SuppressWarnings("unchecked")
                List<Salle> rooms = (List<Salle>) admin.sendRequest(
                        new Request("GET_SALLES", null, null)).data;
                for (Salle room : rooms) {
                    if (roomName.equals(room.nom)) {
                        roomId = room.id;
                        break;
                    }
                }
                check(roomId > 0, "la salle de test doit être consultable");

                String date = LocalDate.now().plusDays(3).toString();
                Reservation ownedReservation = new Reservation(
                        0, 99999, roomId, date, "09:00:00", "10:00:00", "Test réservation", "CONFIRMEE");
                Response created = user1.sendRequest(
                        new Request("ADD_RESERVATION", ownedReservation, null));
                check(created.success && created.data instanceof Integer,
                        "la réservation doit être créée et son identifiant retourné");
                int reservationId = (Integer) created.data;

                @SuppressWarnings("unchecked")
                List<Reservation> user2Reservations = (List<Reservation>) user2.sendRequest(
                        new Request("GET_RESERVATIONS", null, null)).data;
                check(user2Reservations.stream().noneMatch(r -> r.id == reservationId),
                        "un utilisateur ne doit consulter que ses réservations");

                Response deniedDelete = user2.sendRequest(
                        new Request("DELETE_RESERVATION", reservationId, null));
                check(!deniedDelete.success, "un utilisateur ne doit pas supprimer la réservation d'un autre");

                Reservation update = new Reservation(
                        reservationId, 99999, roomId, date, "10:00:00", "11:00:00", "Test modifié", "CONFIRMEE");
                check(user1.sendRequest(new Request("UPDATE_RESERVATION", update, null)).success,
                        "le propriétaire doit pouvoir modifier sa réservation");
                check(user1.sendRequest(new Request("CANCEL_RESERVATION", reservationId, null)).success,
                        "le propriétaire doit pouvoir annuler sa réservation");
                check(user1.sendRequest(new Request("DELETE_RESERVATION", reservationId, null)).success,
                        "le propriétaire doit pouvoir supprimer sa réservation");

                testConcurrentReservations(user1, user2, roomId, LocalDate.now().plusDays(5).toString());

                check(admin.sendRequest(new Request("DELETE_SALLE", roomId, null)).success,
                        "l'administrateur doit pouvoir nettoyer la salle de test");
                roomId = -1;
                System.out.println("DS1 : tous les tests d'intégration sont réussis.");
            } finally {
                if (roomId > 0 && admin != null) {
                    try {
                        admin.sendRequest(new Request("DELETE_SALLE", roomId, null));
                    } catch (IOException ignored) {
                        System.err.println("Nettoyage de la salle de test impossible.");
                    }
                }
                close(anonymous);
                close(admin);
                close(user1);
                close(user2);
            }
        }

        private static void testConcurrentReservations(ReservationClient first, ReservationClient second,
                                                       int roomId, String date) throws Exception {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Response> a = pool.submit(() -> {
                    start.await();
                    return first.sendRequest(new Request("ADD_RESERVATION",
                            new Reservation(0, 0, roomId, date, "13:00:00", "15:00:00",
                                    "Concurrent A", "CONFIRMEE"), null));
                });
                Future<Response> b = pool.submit(() -> {
                    start.await();
                    return second.sendRequest(new Request("ADD_RESERVATION",
                            new Reservation(0, 0, roomId, date, "14:00:00", "16:00:00",
                                    "Concurrent B", "CONFIRMEE"), null));
                });
                start.countDown();
                boolean acceptedA = a.get().success;
                boolean acceptedB = b.get().success;
                check(acceptedA != acceptedB, "un seul créneau chevauchant doit être accepté");
            } finally {
                pool.shutdownNow();
            }
        }

        private static void testFiles() throws Exception {
            File csvFile = File.createTempFile("ds1-test-", ".csv");
            File binaryFile = File.createTempFile("ds1-test-", ".dat");
            try {
                Reservation reservation = new Reservation(
                        7, 2, 3, "2030-01-02", "09:00:00", "10:00:00",
                        "Motif; avec \"guillemets\"\net retour", "CONFIRMEE");
                reservation.nomUtilisateur = "Utilisateur";
                reservation.nomSalle = "Salle; A";
                ArrayList<Reservation> source = new ArrayList<>();
                source.add(reservation);

                FileManager.exportToCSV(source, csvFile.getAbsolutePath());
                String csv = new String(Files.readAllBytes(csvFile.toPath()), StandardCharsets.UTF_8);
                check(csv.contains("\"Salle; A\""), "le CSV doit protéger les champs contenant le séparateur");
                check(csv.contains("\"Motif; avec \"\"guillemets\"\"\net retour\""),
                        "le CSV doit doubler les guillemets et préserver les retours à la ligne");

                FileManager.saveBinary(source, binaryFile.getAbsolutePath());
                List<Reservation> loaded = FileManager.loadBinary(binaryFile.getAbsolutePath());
                check(loaded.size() == 1 && loaded.get(0).motif.equals(reservation.motif),
                        "la sérialisation binaire doit préserver les réservations");
            } finally {
                Files.deleteIfExists(csvFile.toPath());
                Files.deleteIfExists(binaryFile.toPath());
            }
        }

        private static void testConnectionLoss() throws Exception {
            try (ServerSocket fakeServer = new ServerSocket(0)) {
                AtomicReference<Throwable> peerFailure = new AtomicReference<>();
                Thread peer = new Thread(() -> {
                    try (Socket socket = fakeServer.accept();
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.flush();
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        in.readObject();
                    } catch (Exception e) {
                        peerFailure.set(e);
                    }
                }, "ds1-test-disconnecting-peer");
                peer.start();

                ReservationClient client = new ReservationClient("localhost", fakeServer.getLocalPort());
                try {
                    client.sendRequest(new Request("GET_STATS", null, null));
                    throw new AssertionError("la perte de connexion doit remonter comme une IOException");
                } catch (IOException expected) {
                    // Expected: the peer closes without sending a response.
                } finally {
                    client.close();
                }
                peer.join(3000);
                check(!peer.isAlive(), "le serveur de test de perte de connexion doit se terminer");
                check(peerFailure.get() == null, "le pair de test doit recevoir la requête avant de couper la connexion");
            }
        }

        private static ReservationClient login(String email, String password) throws Exception {
            ReservationClient client = connect();
            Response response = client.sendRequest(
                    new Request("LOGIN", new String[]{email, password}, email));
            if (!response.success) {
                client.close();
                throw new AssertionError("Échec d'authentification pour le compte de test " + email + ": " + response.message);
            }
            return client;
        }

        private static ReservationClient connect() throws IOException {
            IOException lastError = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                try {
                    return new ReservationClient("localhost", 5000);
                } catch (IOException e) {
                    lastError = e;
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Attente du serveur interrompue.", interrupted);
                    }
                }
            }
            throw lastError;
        }

        private static void close(ReservationClient client) {
            if (client != null) client.close();
        }

        private static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }

    // =========================================================================
    // 8. POINT D'ENTRÉE PRINCIPAL (LANCEUR SERVEUR / CLIENT)
    // =========================================================================

    public static void main(String[] args) {
        if (args.length > 0) {
            if ("server".equalsIgnoreCase(args[0])) {
                ReservationServer.startServer();
                return;
            } else if ("client".equalsIgnoreCase(args[0])) {
                SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
                return;
            } else if ("test".equalsIgnoreCase(args[0]) || "test-local".equalsIgnoreCase(args[0])) {
                try {
                    IntegrationTest.run("test-local".equalsIgnoreCase(args[0]));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                return;
            }
        }

        String[] options = {"Démarrer le Serveur (Port 5000)", "Lancer un Client Swing"};
        int choice = JOptionPane.showOptionDialog(null,
                "Choisissez le mode de lancement de l'application :",
                "Lanceur DS1 - Réservation de Salles",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 0) {
            new Thread(ReservationServer::startServer).start();
            JOptionPane.showMessageDialog(null, "Serveur démarré sur le port 5000.\nRelancez DS1 pour ouvrir des clients.");
        } else if (choice == 1) {
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }
}
