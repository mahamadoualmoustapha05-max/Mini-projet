import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Fournit une connexion JDBC vers ReservationDB (une connexion par appel = thread-safe). */
public final class DatabaseConnection {
    private static final String URL = System.getProperty("db.url",
            "jdbc:mysql://localhost:3306/ReservationDB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
    private static final String USER = System.getProperty("db.user", "root");
    private static final String PASSWORD = System.getProperty("db.password", "");

    private DatabaseConnection() { }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
