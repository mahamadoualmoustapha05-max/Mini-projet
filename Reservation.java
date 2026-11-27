import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

public class Reservation implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id, idUtilisateur, idSalle;
    private LocalDate date;
    private LocalTime heureDebut, heureFin;
    private String motif, statut;
    // champs d'affichage (remplis par la jointure SQL)
    private String nomSalle, nomUtilisateur;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdUtilisateur() { return idUtilisateur; }
    public void setIdUtilisateur(int v) { this.idUtilisateur = v; }
    public int getIdSalle() { return idSalle; }
    public void setIdSalle(int v) { this.idSalle = v; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getHeureDebut() { return heureDebut; }
    public void setHeureDebut(LocalTime h) { this.heureDebut = h; }
    public LocalTime getHeureFin() { return heureFin; }
    public void setHeureFin(LocalTime h) { this.heureFin = h; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getNomSalle() { return nomSalle; }
    public void setNomSalle(String s) { this.nomSalle = s; }
    public String getNomUtilisateur() { return nomUtilisateur; }
    public void setNomUtilisateur(String s) { this.nomUtilisateur = s; }
}
