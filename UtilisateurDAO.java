import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

public class UtilisateurDAO {

    /** Retourne l'utilisateur (sans mot de passe) si email + mot de passe sont corrects, sinon null. */
    public Utilisateur authenticate(String email, String password) throws SQLException {
        String sql = "SELECT id, nom, email, role FROM Utilisateur WHERE email = ? AND motDePasse = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, sha256(password));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Utilisateur(rs.getInt(1), rs.getString(2), rs.getString(3), null, rs.getString(4));
                }
            }
        }
        return null;
    }

    static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
