import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Traite un client dans son propre thread. */
public class ClientHandler implements Runnable {

    /** Verrou partagé par TOUS les handlers : sérialise les écritures de réservation côté Java.
     *  (La garantie finale reste dans la base : SELECT ... FOR UPDATE, voir ReservationDAO.) */
    private static final Object RESERVATION_LOCK = new Object();

    private final Socket socket;
    private final UtilisateurDAO userDao = new UtilisateurDAO();
    private final SalleDAO salleDao = new SalleDAO();
    private final ReservationDAO resDao = new ReservationDAO();
    private Utilisateur current;   // null tant que non authentifié

    public ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        FileManager.log(remote, "CONNEXION", "Client connecté");
        // IMPORTANT : on crée l'ObjectOutputStream AVANT l'ObjectInputStream (sinon blocage mutuel avec le client)
        try (Socket s = socket; ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.flush();
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            while (true) {
                Request req = (Request) in.readObject();
                Response res = handle(req);
                out.writeObject(res);
                out.flush();
                out.reset();
            }
        } catch (EOFException | SocketException e) {
            // le client s'est déconnecté : cas normal
        } catch (IOException | ClassNotFoundException e) {
            FileManager.log(who(), "ERREUR", e.toString());
        } finally {
            FileManager.log(who(), "DECONNEXION", "Client déconnecté (" + remote + ")");
        }
    }

    private String who() { return current != null ? current.getEmail() : String.valueOf(socket.getRemoteSocketAddress()); }

    private Response handle(Request req) {
        Response res;
        try {
            res = process(req);
        } catch (ReservationDAO.ConflictException e) {
            res = Response.error(e.getMessage());
        } catch (SQLIntegrityConstraintViolationException e) {
            res = Response.error("Contrainte d'intégrité violée (nom déjà utilisé, salle liée, donnée invalide...).");
        } catch (SQLException e) {
            res = Response.error("Erreur base de données : " + e.getMessage());
        } catch (ClassCastException | NullPointerException e) {
            res = Response.error("Requête invalide.");
        }
        FileManager.log(who(), req.getAction().name(), (res.isSuccess() ? "SUCCES" : "ECHEC") + " - " + res.getMessage());
        return res;
    }

    private Response process(Request req) throws SQLException, ReservationDAO.ConflictException {
        if (req.getAction() == Request.Action.LOGIN) {
            String[] cred = (String[]) req.getData();
            Utilisateur u = userDao.authenticate(cred[0], cred[1]);
            if (u == null) return Response.error("Identifiants invalides pour " + cred[0]);
            current = u;
            return Response.ok("Bienvenue " + u.getNom(), u);
        }
        if (current == null) return Response.error("Authentification requise.");

        switch (req.getAction()) {
            case STATS:
                return Response.ok("OK", new LinkedHashMap<>(resDao.stats()));

            case LIST_SALLES:
                return Response.ok("OK", new ArrayList<Salle>(salleDao.findAll()));

            case ADD_SALLE: {
                if (!current.isAdmin()) return Response.error("Action réservée à l'administrateur.");
                Salle s = (Salle) req.getData();
                String err = validate(s);
                if (err != null) return Response.error(err);
                salleDao.create(s);
                return Response.ok("Salle ajoutée.", null);
            }
            case UPDATE_SALLE: {
                if (!current.isAdmin()) return Response.error("Action réservée à l'administrateur.");
                Salle s = (Salle) req.getData();
                String err = validate(s);
                if (err != null) return Response.error(err);
                return salleDao.update(s) > 0 ? Response.ok("Salle modifiée.", null) : Response.error("Salle introuvable.");
            }
            case DELETE_SALLE: {
                if (!current.isAdmin()) return Response.error("Action réservée à l'administrateur.");
                int id = (Integer) req.getData();
                return salleDao.delete(id) > 0 ? Response.ok("Salle supprimée.", null) : Response.error("Salle introuvable.");
            }

            case LIST_RESERVATIONS: {
                String keyword = (String) req.getData();
                Integer uid = current.isAdmin() ? null : Integer.valueOf(current.getId());
                return Response.ok("OK", new ArrayList<Reservation>(resDao.findAll(uid, keyword)));
            }
            case ADD_RESERVATION: {
                Reservation r = (Reservation) req.getData();
                String err = validate(r);
                if (err != null) return Response.error(err);
                r.setIdUtilisateur(current.getId());
                int id;
                synchronized (RESERVATION_LOCK) { id = resDao.create(r); }
                return Response.ok("Réservation n°" + id + " confirmée.", Integer.valueOf(id));
            }
            case UPDATE_RESERVATION: {
                Reservation r = (Reservation) req.getData();
                Reservation existing = resDao.findById(r.getId());
                Response denied = checkAccess(existing);
                if (denied != null) return denied;
                if ("ANNULEE".equals(existing.getStatut())) return Response.error("Impossible de modifier une réservation annulée.");
                String err = validate(r);
                if (err != null) return Response.error(err);
                synchronized (RESERVATION_LOCK) { resDao.update(r); }
                return Response.ok("Réservation modifiée.", null);
            }
            case CANCEL_RESERVATION: {
                int id = (Integer) req.getData();
                Response denied = checkAccess(resDao.findById(id));
                if (denied != null) return denied;
                synchronized (RESERVATION_LOCK) { resDao.cancel(id); }
                return Response.ok("Réservation annulée.", null);
            }
            case DELETE_RESERVATION: {
                int id = (Integer) req.getData();
                Response denied = checkAccess(resDao.findById(id));
                if (denied != null) return denied;
                synchronized (RESERVATION_LOCK) { resDao.delete(id); }
                return Response.ok("Réservation supprimée.", null);
            }
            default:
                return Response.error("Action inconnue.");
        }
    }

    /** Un utilisateur normal ne touche qu'à SES réservations ; l'admin à toutes. */
    private Response checkAccess(Reservation existing) {
        if (existing == null) return Response.error("Réservation introuvable.");
        if (!current.isAdmin() && existing.getIdUtilisateur() != current.getId())
            return Response.error("Accès refusé : cette réservation ne vous appartient pas.");
        return null;
    }

    private String validate(Reservation r) {
        if (r.getIdSalle() <= 0) return "Salle non choisie.";
        if (r.getDate() == null || r.getHeureDebut() == null || r.getHeureFin() == null) return "Date et heures obligatoires.";
        if (!r.getHeureFin().isAfter(r.getHeureDebut())) return "L'heure de fin doit être après l'heure de début.";
        if (r.getDate().isBefore(LocalDate.now())) return "La date ne peut pas être dans le passé.";
        return null;
    }

    private String validate(Salle s) {
        if (s.getNom() == null || s.getNom().isBlank()) return "Le nom de la salle est obligatoire.";
        if (s.getType() == null || s.getType().isBlank()) return "Le type de la salle est obligatoire.";
        if (s.getCapacite() <= 0) return "La capacité doit être positive.";
        return null;
    }
}
