import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class ReservationPanel extends JPanel implements Refreshable {
    private final ReservationClient client;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Salle", "Utilisateur", "Date", "Début", "Fin", "Motif", "Statut"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Reservation> rows = new ArrayList<>();

    private final JComboBox<Salle> cbSalle = new JComboBox<>();
    private final JTextField tfDate = new JTextField(LocalDate.now().plusDays(1).toString(), 9);
    private final JTextField tfDebut = new JTextField("09:00", 5);
    private final JTextField tfFin = new JTextField("11:00", 5);
    private final JTextField tfMotif = new JTextField(18);
    private final JTextField tfSearch = new JTextField(18);

    public ReservationPanel(ReservationClient client) {
        this.client = client;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- recherche ---
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btSearch = new JButton("Rechercher"), btAll = new JButton("Tout afficher");
        north.add(new JLabel("Recherche (salle, motif, date, statut...) :"));
        north.add(tfSearch); north.add(btSearch); north.add(btAll);

        // --- formulaire ---
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Salle")); form.add(cbSalle);
        form.add(new JLabel("Date (aaaa-mm-jj)")); form.add(tfDate);
        form.add(new JLabel("Début")); form.add(tfDebut);
        form.add(new JLabel("Fin")); form.add(tfFin);
        form.add(new JLabel("Motif")); form.add(tfMotif);

        // --- boutons ---
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btAdd = new JButton("Ajouter"), btUpdate = new JButton("Modifier"),
                btCancel = new JButton("Annuler la réservation"), btDelete = new JButton("Supprimer"),
                btCsv = new JButton("Exporter CSV"), btSave = new JButton("Sauvegarder (.ser)"),
                btLoad = new JButton("Charger (.ser)");
        for (JButton b : new JButton[]{btAdd, btUpdate, btCancel, btDelete, btCsv, btSave, btLoad}) buttons.add(b);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(form);
        south.add(buttons);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) fillForm(); });

        btSearch.addActionListener(e -> search());
        tfSearch.addActionListener(e -> search());
        btAll.addActionListener(e -> { tfSearch.setText(""); search(); });
        btAdd.addActionListener(e -> add());
        btUpdate.addActionListener(e -> update());
        btCancel.addActionListener(e -> cancel());
        btDelete.addActionListener(e -> delete());
        btCsv.addActionListener(e -> exportCsv());
        btSave.addActionListener(e -> saveBinary());
        btLoad.addActionListener(e -> loadBinary());
    }

    // ================= chargement =================
    @Override public void refresh() { loadSalles(); search(); }

    @SuppressWarnings("unchecked")
    private void loadSalles() {
        Async.run(this, () -> client.send(new Request(Request.Action.LIST_SALLES)), res -> {
            if (!res.isSuccess()) return;
            cbSalle.removeAllItems();
            for (Salle s : (List<Salle>) res.getData()) if (s.isDisponible()) cbSalle.addItem(s);
        });
    }

    @SuppressWarnings("unchecked")
    private void search() {
        String keyword = tfSearch.getText().trim();
        Async.run(this, () -> client.send(new Request(Request.Action.LIST_RESERVATIONS, keyword)), res -> {
            if (res.isSuccess()) show((List<Reservation>) res.getData());
            else JOptionPane.showMessageDialog(this, res.getMessage());
        });
    }

    private void show(List<Reservation> list) {
        rows = list;
        model.setRowCount(0);
        for (Reservation r : list) {
            model.addRow(new Object[]{r.getId(), r.getNomSalle(), r.getNomUtilisateur(), r.getDate(),
                    r.getHeureDebut(), r.getHeureFin(), r.getMotif(), r.getStatut()});
        }
    }

    // ================= actions CRUD =================
    private void add() {
        try {
            Reservation r = readForm();
            Async.run(this, () -> client.send(new Request(Request.Action.ADD_RESERVATION, r)), this::afterAction);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Saisie invalide", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void update() {
        Reservation sel = requireSelected();
        if (sel == null) return;
        try {
            Reservation r = readForm();
            r.setId(sel.getId());
            Async.run(this, () -> client.send(new Request(Request.Action.UPDATE_RESERVATION, r)), this::afterAction);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Saisie invalide", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void cancel() { sendOnSelected(Request.Action.CANCEL_RESERVATION, "Annuler cette réservation ?"); }
    private void delete() { sendOnSelected(Request.Action.DELETE_RESERVATION, "Supprimer définitivement cette réservation ?"); }

    private void sendOnSelected(Request.Action action, String question) {
        Reservation sel = requireSelected();
        if (sel == null) return;
        if (JOptionPane.showConfirmDialog(this, question, "Confirmation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Integer id = sel.getId();
        Async.run(this, () -> client.send(new Request(action, id)), this::afterAction);
    }

    /** Affiche le message du serveur (succès OU indisponibilité) puis recharge le tableau. */
    private void afterAction(Response res) {
        JOptionPane.showMessageDialog(this, res.getMessage(),
                res.isSuccess() ? "Succès" : "Opération refusée",
                res.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (res.isSuccess()) search();
    }

    // ================= fichiers =================
    private void exportCsv() {
        File f = chooseFile("reservations.csv", true);
        if (f == null) return;
        List<Reservation> copy = new ArrayList<>(rows);
        Async.run(this, () -> { FileManager.exportCsv(f, copy); return f; },
                file -> JOptionPane.showMessageDialog(this, "Export CSV terminé :\n" + file.getAbsolutePath()));
    }

    private void saveBinary() {
        File f = chooseFile("reservations.ser", true);
        if (f == null) return;
        List<Reservation> copy = new ArrayList<>(rows);
        Async.run(this, () -> { FileManager.saveBinary(f, copy); return f; },
                file -> JOptionPane.showMessageDialog(this, "Sauvegarde binaire terminée :\n" + file.getAbsolutePath()));
    }

    private void loadBinary() {
        File f = chooseFile("reservations.ser", false);
        if (f == null) return;
        Async.run(this, () -> FileManager.loadBinary(f), list -> {
            show(list);
            JOptionPane.showMessageDialog(this, list.size() + " réservation(s) lues depuis le fichier (affichage seulement).");
        });
    }

    private File chooseFile(String suggestedName, boolean save) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(suggestedName));
        int r = save ? fc.showSaveDialog(this) : fc.showOpenDialog(this);
        return r == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }

    // ================= formulaire =================
    private Reservation readForm() {
        Salle s = (Salle) cbSalle.getSelectedItem();
        if (s == null) throw new IllegalArgumentException("Choisissez une salle.");
        try {
            Reservation r = new Reservation();
            r.setIdSalle(s.getId());
            r.setDate(LocalDate.parse(tfDate.getText().trim()));
            r.setHeureDebut(LocalTime.parse(tfDebut.getText().trim()));
            r.setHeureFin(LocalTime.parse(tfFin.getText().trim()));
            r.setMotif(tfMotif.getText().trim());
            return r;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format invalide : date = aaaa-mm-jj, heures = hh:mm (ex. 09:30).");
        }
    }

    private void fillForm() {
        Reservation r = selected();
        if (r == null) return;
        for (int i = 0; i < cbSalle.getItemCount(); i++) {
            if (cbSalle.getItemAt(i).getId() == r.getIdSalle()) { cbSalle.setSelectedIndex(i); break; }
        }
        tfDate.setText(r.getDate().toString());
        tfDebut.setText(r.getHeureDebut().toString());
        tfFin.setText(r.getHeureFin().toString());
        tfMotif.setText(r.getMotif() == null ? "" : r.getMotif());
    }

    private Reservation selected() {
        int i = table.getSelectedRow();
        if (i < 0) return null;
        int m = table.convertRowIndexToModel(i);
        return m < rows.size() ? rows.get(m) : null;
    }

    private Reservation requireSelected() {
        Reservation r = selected();
        if (r == null) JOptionPane.showMessageDialog(this, "Sélectionnez d'abord une réservation dans le tableau.");
        return r;
    }
}
