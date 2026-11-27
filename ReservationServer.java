import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/** Serveur TCP : un thread indépendant (ClientHandler) par client connecté. */
public class ReservationServer {
    public static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            FileManager.log("SERVEUR", "DEMARRAGE", "Écoute sur le port " + PORT);
            int n = 0;
            while (true) {
                Socket client = server.accept();                       // bloquant, mais seulement dans le thread principal
                Thread t = new Thread(new ClientHandler(client), "client-" + (++n));
                t.start();                                             // le serveur retourne aussitôt attendre le suivant
            }
        }
    }
}
