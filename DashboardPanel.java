import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class DashboardPanel extends JPanel implements Refreshable {
    private final ReservationClient client;
    private final JPanel grid = new JPanel(new GridLayout(0, 3, 14, 14));

    public DashboardPanel(ReservationClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JButton btRefresh = new JButton("Actualiser");
        btRefresh.addActionListener(e -> refresh());
        add(new JLabel("Statistiques des réservations et des salles", SwingConstants.CENTER), BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        JPanel south = new JPanel();
        south.add(btRefresh);
        add(south, BorderLayout.SOUTH);
    }

    @Override @SuppressWarnings("unchecked")
    public void refresh() {
        Async.run(this, () -> client.send(new Request(Request.Action.STATS)), res -> {
            if (!res.isSuccess()) {
                JOptionPane.showMessageDialog(this, res.getMessage());
                return;
            }
            grid.removeAll();
            ((Map<String, Integer>) res.getData()).forEach((label, value) -> grid.add(card(label, value)));
            grid.revalidate();
            grid.repaint();
        });
    }

    private JPanel card(String label, int value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JLabel v = new JLabel(String.valueOf(value), SwingConstants.CENTER);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 32f));
        p.add(v, BorderLayout.CENTER);
        p.add(new JLabel(label, SwingConstants.CENTER), BorderLayout.SOUTH);
        return p;
    }
}
