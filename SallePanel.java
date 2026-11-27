import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Interface administrateur : ajout / modification / suppression de salles. */
public class SallePanel extends JPanel implements Refreshable {
    private final ReservationClient client;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Nom", "Type", "Capacité", "Disponible"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Salle> rows = new ArrayList<>();

    private final JTextField tfNom = new JTextField(14), tfType = new JTextField(12);
    private final JSpinner spCapacite = new JSpinner(new SpinnerNumberModel(30, 1, 1000, 1));
    private final JCheckBox chkDispo = new JCheckBox("Disponible", true);

    public SallePanel(ReservationClient client) {
        this.client = client;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Nom")); form.add(tfNom);
        form.add(new JLabel("Type")); form.add(tfType);
        form.add(new JLabel("Capacité")); form.add(spCapacite);
        form.add(chkDispo);
        JButton btAdd = new JButton("Ajouter"), btUpdate = new JButton("Modifier"),
                btDelete = new JButton("Supprimer"), btRefresh = new JButton("Actualiser");
        form.add(btAdd); form.add(btUpdate); form.add(btDelete); form.add(btRefresh);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(form, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) fillForm(); });
        btAdd.addActionListener(e -> send(Request.Action.ADD_SALLE, readForm(0)));
        btUpdate.addActionListener(e -> { Salle s = selected(); if (s != null) send(Request.Action.UPDATE_SALLE, readForm(s.getId())); });
        btDelete.addActionListener(e -> {
            Salle s = selected();
            if (s == null) return;
            if (JOptionPane.showConfirmDialog(this, "Supprimer la salle « " + s.getNom() + " » et ses réservations ?",
                    "Confirmation", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                send(Request.Action.DELETE_SALLE, Integer.valueOf(s.getId()));
            }
        });
        btRefresh.addActionListener(e -> refresh());
    }

    @Override @SuppressWarnings("unchecked")
    public void refresh() {
        Async.run(this, () -> client.send(new Request(Request.Action.LIST_SALLES)), res -> {
            if (!res.isSuccess()) { JOptionPane.showMessageDialog(this, res.getMessage()); return; }
            rows = (List<Salle>) res.getData();
            model.setRowCount(0);
            for (Salle s : rows) model.addRow(new Object[]{s.getId(), s.getNom(), s.getType(), s.getCapacite(), s.isDisponible() ? "Oui" : "Non"});
        });
    }

    private void send(Request.Action action, java.io.Serializable data) {
        Async.run(this, () -> client.send(new Request(action, data)), res -> {
            JOptionPane.showMessageDialog(this, res.getMessage(), res.isSuccess() ? "Succès" : "Refusé",
                    res.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            if (res.isSuccess()) refresh();
        });
    }

    private Salle readForm(int id) {
        return new Salle(id, tfNom.getText().trim(), tfType.getText().trim(), (Integer) spCapacite.getValue(), chkDispo.isSelected());
    }

    private Salle selected() {
        int i = table.getSelectedRow();
        if (i < 0 || i >= rows.size()) {
            JOptionPane.showMessageDialog(this, "Sélectionnez d'abord une salle.");
            return null;
        }
        return rows.get(i);
    }

    private void fillForm() {
        int i = table.getSelectedRow();
        if (i < 0 || i >= rows.size()) return;
        Salle s = rows.get(i);
        tfNom.setText(s.getNom()); tfType.setText(s.getType());
        spCapacite.setValue(s.getCapacite()); chkDispo.setSelected(s.isDisponible());
    }
}
