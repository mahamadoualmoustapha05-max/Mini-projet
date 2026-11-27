import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {

    public MainFrame(ReservationClient client, Utilisateur user) {
        super("Réservation de salles - " + user.getNom() + " (" + user.getRole() + ")");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        DashboardPanel dashboard = new DashboardPanel(client);
        tabs.addTab("Tableau de bord", dashboard);
        tabs.addTab("Réservations", new ReservationPanel(client));
        if (user.isAdmin()) tabs.addTab("Administration des salles", new SallePanel(client));
        tabs.addChangeListener(e -> {                      // recharge les données quand on change d'onglet
            if (tabs.getSelectedComponent() instanceof Refreshable r) r.refresh();
        });
        add(tabs);

        // Perte de connexion : on prévient l'utilisateur et on revient à l'écran de connexion
        Async.setConnectionLostHandler(msg -> {
            JOptionPane.showMessageDialog(this, msg + "\nRetour à l'écran d'authentification.",
                    "Connexion perdue", JOptionPane.ERROR_MESSAGE);
            client.close();
            dispose();
            new LoginFrame().setVisible(true);
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { client.close(); System.exit(0); }
        });
        dashboard.refresh();
    }
}
