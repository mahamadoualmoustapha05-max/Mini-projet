import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Test de réservations concurrentes : 3 clients (3 sockets, 3 threads côté serveur) demandent
 * la MÊME salle, le MÊME jour, sur des horaires qui se chevauchent, au même instant (CyclicBarrier).
 * Résultat attendu : exactement 1 succès, 2 messages d'indisponibilité.
 * Usage : java -cp out ConcurrencyTest [idSalle]   (le serveur doit tourner)
 */
public class ConcurrencyTest {
    public static void main(String[] args) throws Exception {
        int salleId = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        LocalDate date = LocalDate.now().plusDays(30 + new Random().nextInt(300));   // jour libre au hasard
        String[][] accounts = {{"user1@res.ma", "user123"}, {"user2@res.ma", "user123"}, {"user3@res.ma", "user123"}};
        LocalTime[][] slots = {
            {LocalTime.of(10, 0), LocalTime.of(12, 0)},
            {LocalTime.of(11, 0), LocalTime.of(13, 0)},
            {LocalTime.of(10, 30), LocalTime.of(11, 30)}};

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<Response>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                final int index = i;
                results.add(pool.submit(() -> {
                    ReservationClient client = new ReservationClient("localhost", ReservationServer.PORT);
                    boolean announcedReady = false;
                    try {
                        client.connect();
                        Response login = client.send(new Request(Request.Action.LOGIN, accounts[index]));
                        if (!login.isSuccess()) {
                            throw new IllegalStateException("Login échoué : " + login.getMessage());
                        }

                        ready.countDown();
                        announcedReady = true;
                        if (!start.await(15, TimeUnit.SECONDS)) {
                            throw new TimeoutException("Départ du test concurrent expiré.");
                        }

                        Reservation reservation = new Reservation();
                        reservation.setIdSalle(salleId);
                        reservation.setDate(date);
                        reservation.setHeureDebut(slots[index][0]);
                        reservation.setHeureFin(slots[index][1]);
                        reservation.setMotif("Test concurrence");
                        return client.send(new Request(Request.Action.ADD_RESERVATION, reservation));
                    } finally {
                        if (!announcedReady) ready.countDown();
                        client.close();
                    }
                }));
            }

            if (!ready.await(15, TimeUnit.SECONDS)) {
                throw new TimeoutException("Les trois clients ne se sont pas connectés à temps.");
            }
            start.countDown();

            int successes = 0;
            List<Integer> reservationIds = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                Response response = results.get(i).get(20, TimeUnit.SECONDS);
                boolean conflict = !response.isSuccess()
                        && (response.getMessage().toLowerCase(Locale.ROOT).contains("indisponible")
                        || response.getMessage().toLowerCase(Locale.ROOT).contains("chevauche"));
                if (response.isSuccess()) {
                    successes++;
                    reservationIds.add((Integer) response.getData());
                } else if (!conflict) {
                    throw new AssertionError("Refus inattendu du client " + (i + 1) + " : "
                            + response.getMessage());
                }
                System.out.printf("Client %d (%s-%s) -> %s : %s%n", i + 1, slots[i][0], slots[i][1],
                        response.isSuccess() ? "ACCEPTÉ" : "REFUSÉ", response.getMessage());
            }
            if (successes != 1) {
                throw new AssertionError("Résultat incorrect : " + successes + " réservation(s) acceptée(s).");
            }

            ReservationClient admin = new ReservationClient("localhost", ReservationServer.PORT);
            try {
                admin.connect();
                Response login = admin.send(new Request(Request.Action.LOGIN,
                        new String[]{"admin@res.ma", "admin123"}));
                if (!login.isSuccess()) throw new IllegalStateException("Connexion admin échouée.");
                for (Integer id : reservationIds) {
                    Response deleted = admin.send(new Request(Request.Action.DELETE_RESERVATION, id));
                    if (!deleted.isSuccess()) throw new AssertionError("Nettoyage du test échoué : " + deleted.getMessage());
                }
            } finally {
                admin.close();
            }
            System.out.println("\nTEST RÉUSSI : un seul client a obtenu la salle.");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
