import javax.swing.SwingUtilities;

/** Point d'entrée du client graphique. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
