import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Couche réseau côté client (Socket + ObjectStreams). Les appels sont synchronisés : une requête à la fois. */
public class ReservationClient {

    /** Perte ou impossibilité de connexion avec le serveur. */
    public static class ConnectionException extends IOException {
        public ConnectionException(String message, Throwable cause) { super(message, cause); }
    }

    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ReservationClient(String host, int port) { this.host = host; this.port = port; }

    public synchronized void connect() throws ConnectionException {
        close();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);   // 3 s max pour se connecter
            socket.setSoTimeout(15000);                                // 15 s max pour une réponse
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            close();
            throw new ConnectionException("Impossible de joindre le serveur " + host + ":" + port, e);
        }
    }

    public synchronized Response send(Request request) throws ConnectionException {
        if (socket == null || socket.isClosed()) throw new ConnectionException("Connexion au serveur perdue.", null);
        try {
            out.writeObject(request);
            out.flush();
            out.reset();
            return (Response) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            close();
            throw new ConnectionException("Connexion au serveur perdue (" + e.getClass().getSimpleName() + ").", e);
        }
    }

    public synchronized void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        socket = null; out = null; in = null;
    }
}
