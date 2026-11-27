import java.io.Serializable;

public class Utilisateur implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String nom, email, motDePasse, role;

    public Utilisateur() { }
    public Utilisateur(int id, String nom, String email, String motDePasse, String role) {
        this.id = id; this.nom = nom; this.email = email; this.motDePasse = motDePasse; this.role = role;
    }
    public int getId() { return id; }
    public String getNom() { return nom; }
    public String getEmail() { return email; }
    public String getMotDePasse() { return motDePasse; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
