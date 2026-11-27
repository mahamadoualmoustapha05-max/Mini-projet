import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final JTextField tfServer = new JTextField("localhost");
    private final JTextField tfEmail = new JTextField();
    private final JPasswordField pfPassword = new JPasswordField();
    private final JButton btLogin = new JButton("Se connecter");

    public LoginFrame() {
        super("Authentification");
        Async.setConnectionLostHandler(null);   // sur cet écran, les erreurs réseau s'affichent simplement
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        form.add(new JLabel("Serveur :"));      form.add(tfServer);
        form.add(new JLabel("Email :"));        form.add(tfEmail);
        form.add(new JLabel("Mot de passe :")); form.add(pfPassword);

        JPanel south = new JPanel();
        south.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        south.add(btLogin);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btLogin);
        btLogin.addActionListener(e -> login());
        pack();
        setSize(380, getHeight());
        setLocationRelativeTo(null);
    }

    private void login() {
        String email = tfEmail.getText().trim();
        String pwd = new String(pfPassword.getPassword());
        if (email.isEmpty() || pwd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Email et mot de passe obligatoires.");
            return;
        }
        ReservationClient client = new ReservationClient(tfServer.getText().trim(), ReservationServer.PORT);
        btLogin.setEnabled(false);
        Async.run(this, () -> {
            client.connect();
            return client.send(new Request(Request.Action.LOGIN, new String[]{email, pwd}));
        }, res -> {
            btLogin.setEnabled(true);
            if (res.isSuccess()) {
                new MainFrame(client, (Utilisateur) res.getData()).setVisible(true);
                dispose();
            } else {
                client.close();
                JOptionPane.showMessageDialog(this, res.getMessage(), "Échec", JOptionPane.WARNING_MESSAGE);
            }
        }, () -> { btLogin.setEnabled(true); client.close(); });
    }
}
