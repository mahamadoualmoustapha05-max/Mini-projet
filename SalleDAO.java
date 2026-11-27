import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SalleDAO {

    public List<Salle> findAll() throws SQLException {
        List<Salle> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nom, type, capacite, disponible FROM Salle ORDER BY nom")) {
            while (rs.next()) {
                list.add(new Salle(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5)));
            }
        }
        return list;
    }

    public void create(Salle s) throws SQLException {
        String sql = "INSERT INTO Salle (nom, type, capacite, disponible) VALUES (?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getNom());
            ps.setString(2, s.getType());
            ps.setInt(3, s.getCapacite());
            ps.setBoolean(4, s.isDisponible());
            ps.executeUpdate();
        }
    }

    public int update(Salle s) throws SQLException {
        String sql = "UPDATE Salle SET nom = ?, type = ?, capacite = ?, disponible = ? WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getNom());
            ps.setString(2, s.getType());
            ps.setInt(3, s.getCapacite());
            ps.setBoolean(4, s.isDisponible());
            ps.setInt(5, s.getId());
            return ps.executeUpdate();
        }
    }

    public int delete(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM Salle WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }
}
