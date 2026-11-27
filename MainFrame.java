import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Date;
import java.sql.Time;
import java.util.List;
import java.util.function.Consumer;
import java.io.*;
import java.net.Socket;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.sql.*;
import java.util.ArrayList;
import java.net.ServerSocket;

public class MainFrame extends JFrame {
    private ReservationClient client;
    private Utilisateur currentUser;

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    private JLabel lblStats = new JLabel("Statistiques : Chargement...");
    private DefaultTableModel resTableModel;
    private JTable resTable;
    private DefaultTableModel salleTableModel;
    private JTable salleTable;
    private JComboBox<Salle> comboSalles = new JComboBox<>();

    public MainFrame() {
        setTitle("Gestion des Réservations de Salles - ENSA");
        setSize(980, 620);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        client = new ReservationClient();
        try {
            client.connect("localhost", 5000);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Impossible de se connecter au serveur sur le port 5000.\nAssurez-vous que ReservationServer est lancé.",
                "Erreur Réseau", JOptionPane.ERROR_MESSAGE);
        }

        mainPanel.add(creerEcranLogin(), "LOGIN");
        add(mainPanel);
    }

    private void executerRequeteAsync(Request req, Consumer<Response> onSuccess) {
        new SwingWorker<Response, Void>() {
            @Override
            protected Response doInBackground() {
                return client.sendRequest(req);
            }

            @Override
            protected void done() {
                try {
                    Response res = get();
                    if (!res.isSuccess() && res.getMessage().contains("réseau")) {
                        JOptionPane.showMessageDialog(MainFrame.this, res.getMessage(), "Perte de connexion", JOptionPane.ERROR_MESSAGE);
                    }
                    onSuccess.accept(res);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private JPanel creerEcranLogin() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        JLabel title = new JLabel("Authentification - Réservation de Salles");
        title.setFont(new Font("Arial", Font.BOLD, 18));

        JTextField txtEmail = new JTextField("admin@ensa.ma", 22);
        JPasswordField txtPass = new JPasswordField("admin123", 22);
        JButton btnLogin = new JButton("Se connecter");

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; panel.add(title, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Email :"), gbc);
        gbc.gridx = 1; panel.add(txtEmail, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Mot de passe :"), gbc);
        gbc.gridx = 1; panel.add(txtPass, gbc);
        gbc.gridx = 1; gbc.gridy = 3; panel.add(btnLogin, gbc);

        btnLogin.addActionListener(e -> {
            String[] creds = {txtEmail.getText(), new String(txtPass.getPassword())};
            executerRequeteAsync(new Request("LOGIN", creds), res -> {
                if (res.isSuccess()) {
                    currentUser = (Utilisateur) res.getData();
                    mainPanel.add(creerEcranPrincipal(), "APP");
                    cardLayout.show(mainPanel, "APP");
                    rafraichirDonnees();
                } else {
                    JOptionPane.showMessageDialog(this, res.getMessage(), "Échec d'authentification", JOptionPane.WARNING_MESSAGE);
                }
            });
        });
        return panel;
    }

    private JPanel creerEcranPrincipal() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(235, 242, 250));
        lblStats.setFont(new Font("Arial", Font.BOLD, 13));
        topPanel.add(lblStats, BorderLayout.WEST);

        JButton btnRefresh = new JButton("Actualiser");
        btnRefresh.addActionListener(e -> rafraichirDonnees());
        topPanel.add(btnRefresh, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Gestion des Réservations", creerOngletReservations());
        if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            tabs.addTab("Administration des Salles", creerOngletAdminSalles());
        }
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel creerOngletReservations() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField txtDate = new JTextField("2026-12-01", 8);
        JTextField txtDebut = new JTextField("09:00:00", 6);
        JTextField txtFin = new JTextField("11:00:00", 6);
        JTextField txtMotif = new JTextField("Cours Java", 12);
        JButton btnReserver = new JButton("Réserver");

        formPanel.add(new JLabel("Salle:")); formPanel.add(comboSalles);
        formPanel.add(new JLabel("Date:")); formPanel.add(txtDate);
        formPanel.add(new JLabel("Début:")); formPanel.add(txtDebut);
        formPanel.add(new JLabel("Fin:")); formPanel.add(txtFin);
        formPanel.add(new JLabel("Motif:")); formPanel.add(txtMotif);
        formPanel.add(btnReserver);

        btnReserver.addActionListener(e -> {
            Salle s = (Salle) comboSalles.getSelectedItem();
            if (s == null) return;
            try {
                Reservation r = new Reservation(0, currentUser.getId(), s.getId(),
                        Date.valueOf(txtDate.getText()), Time.valueOf(txtDebut.getText()),
                        Time.valueOf(txtFin.getText()), txtMotif.getText(), "CONFIRMEE");
                executerRequeteAsync(new Request("ADD_RES", r), res -> {
                    JOptionPane.showMessageDialog(this, res.getMessage());
                    if (res.isSuccess()) rafraichirDonnees();
                });
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Format invalide (Date: YYYY-MM-DD, Heure: HH:MM:SS)");
            }
        });
        panel.add(formPanel, BorderLayout.NORTH);

        String[] cols = {"ID", "ID User", "ID Salle", "Date", "Début", "Fin", "Motif", "Statut"};
        resTableModel = new DefaultTableModel(cols, 0);
        resTable = new JTable(resTableModel);
        panel.add(new JScrollPane(resTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField txtSearch = new JTextField(10);
        JButton btnSearch = new JButton("Filtrer Motif");
        JButton btnModifier = new JButton("Modifier Motif");
        JButton btnAnnuler = new JButton("Annuler Rés.");
        JButton btnExport = new JButton("Exporter CSV & Binaire");
        JButton btnLireBin = new JButton("Lire Fichier Binaire");

        bottomPanel.add(new JLabel("Recherche:")); bottomPanel.add(txtSearch);
        bottomPanel.add(btnSearch); bottomPanel.add(btnModifier);
        bottomPanel.add(btnAnnuler); bottomPanel.add(btnExport); bottomPanel.add(btnLireBin);

        btnSearch.addActionListener(e -> {
            String filtre = txtSearch.getText().toLowerCase();
            executerRequeteAsync(new Request("GET_RES", null), res -> {
                if (res.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    List<Reservation> list = (List<Reservation>) res.getData();
                    resTableModel.setRowCount(0);
                    for (Reservation r : list) {
                        if (r.getMotif().toLowerCase().contains(filtre)) {
                            resTableModel.addRow(new Object[]{r.getId(), r.getIdUtilisateur(), r.getIdSalle(),
                                    r.getDate(), r.getHeureDebut(), r.getHeureFin(), r.getMotif(), r.getStatut()});
                        }
                    }
                }
            });
        });

        btnModifier.addActionListener(e -> {
            int row = resTable.getSelectedRow();
            if (row == -1) return;
            String nouveauMotif = JOptionPane.showInputDialog(this, "Nouveau motif :", resTableModel.getValueAt(row, 6));
            if (nouveauMotif != null) {
                Reservation r = new Reservation((int) resTableModel.getValueAt(row, 0),
                        (int) resTableModel.getValueAt(row, 1), (int) resTableModel.getValueAt(row, 2),
                        (Date) resTableModel.getValueAt(row, 3), (Time) resTableModel.getValueAt(row, 4),
                        (Time) resTableModel.getValueAt(row, 5), nouveauMotif, (String) resTableModel.getValueAt(row, 7));
                executerRequeteAsync(new Request("UPDATE_RES", r), res -> rafraichirDonnees());
            }
        });

        btnAnnuler.addActionListener(e -> {
            int row = resTable.getSelectedRow();
            if (row == -1) return;
            int idRes = (int) resTableModel.getValueAt(row, 0);
            executerRequeteAsync(new Request("CANCEL_RES", idRes), res -> rafraichirDonnees());
        });

        btnExport.addActionListener(e -> executerRequeteAsync(new Request("EXPORT_FILES", null),
                res -> JOptionPane.showMessageDialog(this, res.getMessage())));

        btnLireBin.addActionListener(e -> {
            try {
                List<Reservation> binList = FileManager.chargerBinaire("reservations.dat");
                JOptionPane.showMessageDialog(this, "Lecture binaire réussie : " + binList.size() + " réservations lues depuis reservations.dat.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur lecture binaire (Exportez d'abord) : " + ex.getMessage());
            }
        });

        panel.add(bottomPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel creerOngletAdminSalles() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField txtNom = new JTextField(10);
        JTextField txtType = new JTextField(10);
        JTextField txtCap = new JTextField(5);
        JButton btnAdd = new JButton("Ajouter Salle");
        JButton btnEdit = new JButton("Modifier Sélection");
        JButton btnDel = new JButton("Supprimer Sélection");

        form.add(new JLabel("Nom:")); form.add(txtNom);
        form.add(new JLabel("Type:")); form.add(txtType);
        form.add(new JLabel("Capacité:")); form.add(txtCap);
        form.add(btnAdd); form.add(btnEdit); form.add(btnDel);

        btnAdd.addActionListener(e -> {
            try {
                Salle s = new Salle(0, txtNom.getText(), txtType.getText(), Integer.parseInt(txtCap.getText()), true);
                executerRequeteAsync(new Request("ADD_SALLE", s), res -> rafraichirDonnees());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La capacité doit être un nombre entier.");
            }
        });

        btnEdit.addActionListener(e -> {
            int row = salleTable.getSelectedRow();
            if (row == -1) return;
            try {
                int idSalle = (int) salleTableModel.getValueAt(row, 0);
                Salle s = new Salle(idSalle, txtNom.getText(), txtType.getText(), Integer.parseInt(txtCap.getText()), true);
                executerRequeteAsync(new Request("UPDATE_SALLE", s), res -> rafraichirDonnees());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Veuillez remplir Nom, Type et Capacité valide.");
            }
        });

        btnDel.addActionListener(e -> {
            int row = salleTable.getSelectedRow();
            if (row != -1) {
                int idSalle = (int) salleTableModel.getValueAt(row, 0);
                executerRequeteAsync(new Request("DEL_SALLE", idSalle), res -> rafraichirDonnees());
            }
        });

        panel.add(form, BorderLayout.NORTH);

        salleTableModel = new DefaultTableModel(new String[]{"ID", "Nom", "Type", "Capacité", "Disponible"}, 0);
        salleTable = new JTable(salleTableModel);
        panel.add(new JScrollPane(salleTable), BorderLayout.CENTER);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private void rafraichirDonnees() {
        executerRequeteAsync(new Request("GET_SALLES", null), resSalles -> {
            if (resSalles.isSuccess()) {
                List<Salle> salles = (List<Salle>) resSalles.getData();
                comboSalles.removeAllItems();
                if (salleTableModel != null) salleTableModel.setRowCount(0);
                for (Salle s : salles) {
                    comboSalles.addItem(s);
                    if (salleTableModel != null) {
                        salleTableModel.addRow(new Object[]{s.getId(), s.getNom(), s.getType(), s.getCapacite(), s.isDisponible()});
                    }
                }

                executerRequeteAsync(new Request("GET_RES", null), resList -> {
                    if (resList.isSuccess()) {
                        List<Reservation> reservations = (List<Reservation>) resList.getData();
                        resTableModel.setRowCount(0);
                        long actives = 0;
                        for (Reservation r : reservations) {
                            if ("CONFIRMEE".equals(r.getStatut())) actives++;
                            resTableModel.addRow(new Object[]{r.getId(), r.getIdUtilisateur(), r.getIdSalle(),
                                    r.getDate(), r.getHeureDebut(), r.getHeureFin(), r.getMotif(), r.getStatut()});
                        }
                        lblStats.setText("Utilisateur : " + currentUser.getNom() + " (" + currentUser.getRole() + ")   |   " +
                                "Salles : " + salles.size() + "   |   Réservations actives : " + actives + " / " + reservations.size());
                    }
                });
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}

class ReservationClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    public synchronized Response sendRequest(Request req) {
        try {
            if (socket == null || socket.isClosed()) {
                return new Response(false, "Connexion au serveur perdue.", null);
            }
            out.writeObject(req);
            out.flush();
            return (Response) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return new Response(false, "Erreur réseau (Serveur injoignable) : " + e.getMessage(), null);
        }
    }
}

class Reservation implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private int idUtilisateur;
    private int idSalle;
    private Date date;
    private Time heureDebut;
    private Time heureFin;
    private String motif;
    private String statut;

    public Reservation(int id, int idUtilisateur, int idSalle, Date date, Time heureDebut, Time heureFin, String motif, String statut) {
        this.id = id;
        this.idUtilisateur = idUtilisateur;
        this.idSalle = idSalle;
        this.date = date;
        this.heureDebut = heureDebut;
        this.heureFin = heureFin;
        this.motif = motif;
        this.statut = statut;
    }

    public int getId() { return id; }
    public int getIdUtilisateur() { return idUtilisateur; }
    public int getIdSalle() { return idSalle; }
    public Date getDate() { return date; }
    public Time getHeureDebut() { return heureDebut; }
    public Time getHeureFin() { return heureFin; }
    public String getMotif() { return motif; }
    public String getStatut() { return statut; }
}

class Salle implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String nom;
    private String type;
    private int capacite;
    private boolean disponible;

    public Salle(int id, String nom, String type, int capacite, boolean disponible) {
        this.id = id;
        this.nom = nom;
        this.type = type;
        this.capacite = capacite;
        this.disponible = disponible;
    }

    public int getId() { return id; }
    public String getNom() { return nom; }
    public String getType() { return type; }
    public int getCapacite() { return capacite; }
    public boolean isDisponible() { return disponible; }

    @Override
    public String toString() {
        return id + " - " + nom + " (" + type + ", Cap: " + capacite + ")";
    }
}

class Utilisateur implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String nom;
    private String email;
    private String motDePasse;
    private String role;

    public Utilisateur(int id, String nom, String email, String motDePasse, String role) {
        this.id = id;
        this.nom = nom;
        this.email = email;
        this.motDePasse = motDePasse;
        this.role = role;
    }

    public int getId() { return id; }
    public String getNom() { return nom; }
    public String getEmail() { return email; }
    public String getMotDePasse() { return motDePasse; }
    public String getRole() { return role; }
}

class Request implements Serializable {
    private static final long serialVersionUID = 1L;
    private String action;
    private Object payload;

    public Request(String action, Object payload) {
        this.action = action;
        this.payload = payload;
    }

    public String getAction() { return action; }
    public Object getPayload() { return payload; }
}

class Response implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean success;
    private String message;
    private Object data;

    public Response(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
}

class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/ReservationDB?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}

class FileManager {

    public static synchronized void log(String utilisateur, String operation, String resultat) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("logs.txt", true)))) {
            out.println(LocalDateTime.now() + " | Utilisateur: " + utilisateur + " | Opération: " + operation + " | Résultat: " + resultat);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void exporterCSV(List<Reservation> reservations, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("ID;ID_Utilisateur;ID_Salle;Date;Heure_Debut;Heure_Fin;Motif;Statut");
            for (Reservation r : reservations) {
                writer.println(r.getId() + ";" + r.getIdUtilisateur() + ";" + r.getIdSalle() + ";" +
                        r.getDate() + ";" + r.getHeureDebut() + ";" + r.getHeureFin() + ";" +
                        r.getMotif() + ";" + r.getStatut());
            }
        }
    }

    public static void sauvegarderBinaire(List<Reservation> reservations, String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(reservations);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Reservation> chargerBinaire(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            return (List<Reservation>) ois.readObject();
        }
    }
}

class ReservationDAO {

    public Utilisateur authentifier(String email, String mdp) throws SQLException {
        String sql = "SELECT * FROM Utilisateur WHERE email = ? AND motDePasse = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, mdp);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Utilisateur(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("email"),
                        rs.getString("motDePasse"),
                        rs.getString("role")
                    );
                }
            }
        }
        return null;
    }

    public List<Salle> getAllSalles() throws SQLException {
        List<Salle> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM Salle")) {
            while (rs.next()) {
                list.add(new Salle(
                    rs.getInt("id"),
                    rs.getString("nom"),
                    rs.getString("type"),
                    rs.getInt("capacite"),
                    rs.getBoolean("disponible")
                ));
            }
        }
        return list;
    }

    public void ajouterSalle(Salle s) throws SQLException {
        String sql = "INSERT INTO Salle (nom, type, capacite, disponible) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getNom());
            ps.setString(2, s.getType());
            ps.setInt(3, s.getCapacite());
            ps.setBoolean(4, s.isDisponible());
            ps.executeUpdate();
        }
    }

    public void modifierSalle(Salle s) throws SQLException {
        String sql = "UPDATE Salle SET nom = ?, type = ?, capacite = ?, disponible = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getNom());
            ps.setString(2, s.getType());
            ps.setInt(3, s.getCapacite());
            ps.setBoolean(4, s.isDisponible());
            ps.setInt(5, s.getId());
            ps.executeUpdate();
        }
    }

    public void supprimerSalle(int idSalle) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM Salle WHERE id = ?")) {
            ps.setInt(1, idSalle);
            ps.executeUpdate();
        }
    }

    public synchronized boolean ajouterReservation(Reservation r) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            verrouillerSalle(conn, r.getIdSalle());
            String checkSql = "SELECT COUNT(*) FROM Reservation WHERE idSalle = ? AND date = ? " +
                              "AND statut != 'ANNULEE' AND (heureDebut < ? AND heureFin > ?)";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, r.getIdSalle());
                checkPs.setDate(2, r.getDate());
                checkPs.setTime(3, r.getHeureFin());
                checkPs.setTime(4, r.getHeureDebut());
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            String insertSql = "INSERT INTO Reservation (idUtilisateur, idSalle, date, heureDebut, heureFin, motif, statut) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setInt(1, r.getIdUtilisateur());
                insertPs.setInt(2, r.getIdSalle());
                insertPs.setDate(3, r.getDate());
                insertPs.setTime(4, r.getHeureDebut());
                insertPs.setTime(5, r.getHeureFin());
                insertPs.setString(6, r.getMotif());
                insertPs.setString(7, "CONFIRMEE");
                insertPs.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public List<Reservation> getAllReservations() throws SQLException {
        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM Reservation")) {
            while (rs.next()) {
                list.add(new Reservation(
                    rs.getInt("id"),
                    rs.getInt("idUtilisateur"),
                    rs.getInt("idSalle"),
                    rs.getDate("date"),
                    rs.getTime("heureDebut"),
                    rs.getTime("heureFin"),
                    rs.getString("motif"),
                    rs.getString("statut")
                ));
            }
        }
        return list;
    }

    public synchronized boolean modifierReservation(Reservation r) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int salleId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT idSalle FROM Reservation WHERE id = ?")) {
                    ps.setInt(1, r.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        salleId = rs.getInt(1);
                    }
                }

                verrouillerSalle(conn, salleId);
                String checkSql = "SELECT COUNT(*) FROM Reservation WHERE idSalle = ? AND date = ? " +
                        "AND id <> ? AND statut != 'ANNULEE' AND (heureDebut < ? AND heureFin > ?)";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setInt(1, salleId);
                    ps.setDate(2, r.getDate());
                    ps.setInt(3, r.getId());
                    ps.setTime(4, r.getHeureFin());
                    ps.setTime(5, r.getHeureDebut());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                String sql = "UPDATE Reservation SET date=?, heureDebut=?, heureFin=?, motif=? WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, r.getDate());
                    ps.setTime(2, r.getHeureDebut());
                    ps.setTime(3, r.getHeureFin());
                    ps.setString(4, r.getMotif());
                    ps.setInt(5, r.getId());
                    boolean changed = ps.executeUpdate() > 0;
                    conn.commit();
                    return changed;
                }
            } catch (SQLException exception) {
                conn.rollback();
                throw exception;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static void verrouillerSalle(Connection conn, int salleId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM Salle WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, salleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Salle introuvable.");
                }
            }
        }
    }

    public synchronized boolean annulerReservation(int idRes) throws SQLException {
        String sql = "UPDATE Reservation SET statut = 'ANNULEE' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idRes);
            return ps.executeUpdate() > 0;
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private ReservationDAO dao;
    private String currentUserEmail = "Anonyme";

    public ClientHandler(Socket socket, ReservationDAO dao) {
        this.socket = socket;
        this.dao = dao;
    }

    @Override
    public void run() {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.flush();

            Request req;
            while ((req = (Request) in.readObject()) != null) {
                Response res = traiterRequete(req);
                out.writeObject(res);
                out.flush();
            }
        } catch (EOFException e) {
            System.out.println("Client déconnecté : " + currentUserEmail);
        } catch (Exception e) {
            System.err.println("Erreur client (" + currentUserEmail + ") : " + e.getMessage());
        }
    }

    private Response traiterRequete(Request req) {
        try {
            switch (req.getAction()) {
                case "LOGIN":
                    String[] creds = (String[]) req.getPayload();
                    Utilisateur u = dao.authentifier(creds[0], creds[1]);
                    if (u != null) {
                        currentUserEmail = u.getEmail();
                        FileManager.log(currentUserEmail, "LOGIN", "SUCCÈS");
                        return new Response(true, "Bienvenue " + u.getNom(), u);
                    }
                    FileManager.log(creds[0], "LOGIN", "ÉCHEC");
                    return new Response(false, "Email ou mot de passe incorrect.", null);

                case "GET_SALLES":
                    return new Response(true, "Liste des salles récupérée", dao.getAllSalles());

                case "ADD_SALLE":
                    dao.ajouterSalle((Salle) req.getPayload());
                    FileManager.log(currentUserEmail, "ADD_SALLE", "SUCCÈS");
                    return new Response(true, "Salle ajoutée avec succès.", null);

                case "UPDATE_SALLE":
                    dao.modifierSalle((Salle) req.getPayload());
                    FileManager.log(currentUserEmail, "UPDATE_SALLE", "SUCCÈS");
                    return new Response(true, "Salle modifiée avec succès.", null);

                case "DEL_SALLE":
                    dao.supprimerSalle((Integer) req.getPayload());
                    FileManager.log(currentUserEmail, "DEL_SALLE", "SUCCÈS");
                    return new Response(true, "Salle supprimée avec succès.", null);

                case "GET_RES":
                    return new Response(true, "Liste des réservations récupérée", dao.getAllReservations());

                case "ADD_RES":
                    boolean ok = dao.ajouterReservation((Reservation) req.getPayload());
                    FileManager.log(currentUserEmail, "ADD_RES", ok ? "SUCCÈS" : "CONFLIT");
                    if (ok) return new Response(true, "Réservation confirmée !", null);
                    else return new Response(false, "Indisponible : Salle déjà réservée sur ce créneau !", null);

                case "UPDATE_RES":
                    dao.modifierReservation((Reservation) req.getPayload());
                    FileManager.log(currentUserEmail, "UPDATE_RES", "SUCCÈS");
                    return new Response(true, "Réservation modifiée avec succès.", null);

                case "CANCEL_RES":
                    dao.annulerReservation((Integer) req.getPayload());
                    FileManager.log(currentUserEmail, "CANCEL_RES", "SUCCÈS");
                    return new Response(true, "Réservation annulée.", null);

                case "EXPORT_FILES":
                    List<Reservation> all = dao.getAllReservations();
                    FileManager.exporterCSV(all, "reservations.csv");
                    FileManager.sauvegarderBinaire(all, "reservations.dat");
                    FileManager.log(currentUserEmail, "EXPORT_CSV_BIN", "SUCCÈS");
                    return new Response(true, "Fichiers générés : reservations.csv et reservations.dat", all);

                default:
                    return new Response(false, "Action non reconnue.", null);
            }
        } catch (Exception e) {
            FileManager.log(currentUserEmail, req.getAction(), "ERREUR: " + e.getMessage());
            return new Response(false, "Erreur serveur : " + e.getMessage(), null);
        }
    }
}

class ReservationServer {
    public static void main(String[] args) {
        int port = 5000;
        ReservationDAO dao = new ReservationDAO();
        System.out.println("=========================================================");
        System.out.println(" Serveur de Réservation de Salles démarré sur port " + port);
        System.out.println("=========================================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNEXION] Nouveau client : " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket, dao)).start();
            }
        } catch (Exception e) {
            System.err.println("Erreur fatale du serveur : " + e.getMessage());
            e.printStackTrace();
        }
    }
}