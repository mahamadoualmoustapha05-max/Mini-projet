import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Gestion des fichiers : logs.txt, export CSV, sauvegarde binaire (sérialisation Java). */
public final class FileManager {
    private static final File LOG_FILE = new File("logs.txt");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FileManager() { }

    /** Ajoute une ligne : date | utilisateur | opération | résultat. synchronized : plusieurs threads écrivent. */
    public static synchronized void log(String user, String operation, String result) {
        String line = LocalDateTime.now().format(FMT) + " | " + user + " | " + operation + " | " + result;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8))) {
            pw.println(line);
        } catch (IOException e) {
            System.err.println("Écriture du log impossible : " + e.getMessage());
        }
        System.out.println(line);
    }

    public static void exportCsv(File file, List<Reservation> list) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write('\uFEFF'); // BOM pour qu'Excel lise bien les accents
            w.write("id;salle;utilisateur;date;heureDebut;heureFin;motif;statut");
            w.newLine();
            for (Reservation r : list) {
                w.write(String.join(";", String.valueOf(r.getId()), esc(r.getNomSalle()), esc(r.getNomUtilisateur()),
                        String.valueOf(r.getDate()), String.valueOf(r.getHeureDebut()), String.valueOf(r.getHeureFin()),
                        esc(r.getMotif()), esc(r.getStatut())));
                w.newLine();
            }
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return (v.contains(";") || v.contains("\"") || v.contains("\n")) ? "\"" + v + "\"" : v;
    }

    public static void saveBinary(File file, List<Reservation> list) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeObject(new ArrayList<>(list));
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Reservation> loadBinary(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return (List<Reservation>) in.readObject();
        }
    }
}
