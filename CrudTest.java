import java.io.File;
import java.net.ServerSocket;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tests : authentification, CRUD, droits, fichiers (CSV + binaire), perte de connexion.
 * Usage : java -cp out CrudTest   (le serveur doit tourner)
 */
public class CrudTest {
    static int failures = 0;

    static void check(String name, boolean condition) {
        System.out.println((condition ? "[OK]   " : "[FAIL] ") + name);
        if (!condition) failures++;
    }

    static ReservationClient login(String email, String pwd) throws Exception {
        ReservationClient c = new ReservationClient("localhost", ReservationServer.PORT);
        c.connect();
        Response response = c.send(new Request(Request.Action.LOGIN, new String[]{email, pwd}));
        if (!response.isSuccess()) {
            c.close();
            throw new IllegalStateException("Connexion échouée pour " + email + " : " + response.getMessage());
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        // --- 1. Authentification ---
        ReservationClient bad = new ReservationClient("localhost", ReservationServer.PORT);
        bad.connect();
        check("Login refusé (mauvais mot de passe)",
                !bad.send(new Request(Request.Action.LOGIN, new String[]{"user1@res.ma", "faux"})).isSuccess());
        check("Opération refusée sans authentification",
                !bad.send(new Request(Request.Action.LIST_SALLES)).isSuccess());
        bad.close();

        ReservationClient u1 = login("user1@res.ma", "user123");
        ReservationClient u2 = login("user2@res.ma", "user123");
        ReservationClient admin = login("admin@res.ma", "admin123");

        // --- 2. Consultation des salles ---
        Response salles = u1.send(new Request(Request.Action.LIST_SALLES));
        check("Consultation des salles", salles.isSuccess() && !((List<Salle>) salles.getData()).isEmpty());

        // --- 3. CRUD réservation ---
        Reservation r = new Reservation();
        r.setIdSalle(2);
        r.setDate(LocalDate.now().plusDays(400 + (int) (Math.random() * 300)));
        r.setHeureDebut(LocalTime.of(8, 0));
        r.setHeureFin(LocalTime.of(9, 0));
        r.setMotif("Test CRUD");
        Response add = u1.send(new Request(Request.Action.ADD_RESERVATION, r));
        check("CREATE réservation", add.isSuccess());
        int id = (Integer) add.getData();

        r.setId(id);
        r.setHeureFin(LocalTime.of(10, 0));
        r.setMotif("Test CRUD modifié");
        check("UPDATE réservation (propriétaire)", u1.send(new Request(Request.Action.UPDATE_RESERVATION, r)).isSuccess());
        check("UPDATE refusé pour un autre utilisateur", !u2.send(new Request(Request.Action.UPDATE_RESERVATION, r)).isSuccess());

        Response list = u1.send(new Request(Request.Action.LIST_RESERVATIONS, "CRUD modifié"));
        List<Reservation> found = (List<Reservation>) list.getData();
        check("READ + recherche par mot-clé", found.stream().anyMatch(x -> x.getId() == id));

        // --- 4. Fichiers ---
        File csv = new File("test_export.csv"), ser = new File("test_backup.ser");
        FileManager.exportCsv(csv, found);
        FileManager.saveBinary(ser, found);
        check("Export CSV créé", csv.exists() && csv.length() > 0);
        check("Sauvegarde binaire relue à l'identique", FileManager.loadBinary(ser).size() == found.size());

        // --- 5. Annulation / suppression / droits admin ---
        check("CANCEL (propriétaire)", u1.send(new Request(Request.Action.CANCEL_RESERVATION, Integer.valueOf(id))).isSuccess());
        check("UPDATE refusé sur réservation annulée", !u1.send(new Request(Request.Action.UPDATE_RESERVATION, r)).isSuccess());
        check("Un USER ne peut pas ajouter de salle",
                !u1.send(new Request(Request.Action.ADD_SALLE, new Salle(0, "X", "Y", 10, true))).isSuccess());
        check("DELETE par l'admin", admin.send(new Request(Request.Action.DELETE_RESERVATION, Integer.valueOf(id))).isSuccess());
        check("STATS disponibles", admin.send(new Request(Request.Action.STATS)).isSuccess());
        csv.delete(); ser.delete();
        u1.close(); u2.close(); admin.close();

        // --- 6. Perte de connexion : un faux serveur qui raccroche aussitôt ---
        try (ServerSocket fake = new ServerSocket(5999)) {
            Thread t = new Thread(() -> { try { fake.accept().close(); } catch (Exception ignored) { } });
            t.start();
            ReservationClient lost = new ReservationClient("localhost", 5999);
            boolean detected = false;
            try {
                lost.connect();
                lost.send(new Request(Request.Action.LIST_SALLES));
            } catch (ReservationClient.ConnectionException e) {
                detected = true;
                System.out.println("       message : " + e.getMessage());
            }
            check("Perte de connexion détectée (ConnectionException)", detected);
        }

        if (failures == 0) {
            System.out.println("\nTOUS LES TESTS SONT PASSÉS");
        } else {
            System.err.println("\n" + failures + " TEST(S) EN ÉCHEC");
            System.exit(1);
        }
    }
}
