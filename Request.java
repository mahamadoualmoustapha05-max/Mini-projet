import java.io.Serializable;

/** Message envoyé par le client au serveur. */
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Action {
        LOGIN, STATS,
        LIST_SALLES, ADD_SALLE, UPDATE_SALLE, DELETE_SALLE,
        LIST_RESERVATIONS, ADD_RESERVATION, UPDATE_RESERVATION, CANCEL_RESERVATION, DELETE_RESERVATION
    }

    private final Action action;
    private final Serializable data;

    public Request(Action action, Serializable data) { this.action = action; this.data = data; }
    public Request(Action action) { this(action, null); }

    public Action getAction() { return action; }
    public Serializable getData() { return data; }
}
