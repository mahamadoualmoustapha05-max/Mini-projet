import javax.swing.*;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Exécute le travail lourd (réseau / JDBC / fichiers) hors du thread Swing grâce à SwingWorker. */
public final class Async {
    private static volatile Consumer<String> connectionLostHandler;

    private Async() { }

    public static void setConnectionLostHandler(Consumer<String> handler) { connectionLostHandler = handler; }

    public static <T> void run(Component parent, Callable<T> task, Consumer<T> onSuccess) {
        run(parent, task, onSuccess, null);
    }

    public static <T> void run(Component parent, Callable<T> task, Consumer<T> onSuccess, Runnable onError) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }   // thread de fond

            @Override protected void done() {                                                  // thread Swing (EDT)
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    if (onError != null) onError.run();
                    handleError(parent, e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private static void handleError(Component parent, Throwable cause) {
        if (cause instanceof ReservationClient.ConnectionException) {
            Consumer<String> h = connectionLostHandler;
            if (h != null) {
                connectionLostHandler = null;          // une seule alerte même si plusieurs requêtes échouent
                h.accept(cause.getMessage());
                return;
            }
        }
        JOptionPane.showMessageDialog(parent, cause.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
    }
}
