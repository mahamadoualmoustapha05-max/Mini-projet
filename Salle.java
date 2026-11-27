import java.io.Serializable;

public class Salle implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String nom, type;
    private int capacite;
    private boolean disponible;

    public Salle() { }
    public Salle(int id, String nom, String type, int capacite, boolean disponible) {
        this.id = id; this.nom = nom; this.type = type; this.capacite = capacite; this.disponible = disponible;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getCapacite() { return capacite; }
    public void setCapacite(int capacite) { this.capacite = capacite; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    @Override public String toString() { return nom + " (" + type + ", " + capacite + " pl.)"; }
}
