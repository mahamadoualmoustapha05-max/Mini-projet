# Jeu de billard Java

Petit jeu de billard de bureau realise en Java Swing, sans dependance externe.

## Modes de jeu

- Entrainement solo (clic droit pour replacer la boule blanche)
- Deux joueurs
- Contre l'IA : facile, moyen ou difficile

Pendant une partie, faites glisser la souris depuis la boule blanche pour viser et regler la puissance du tir. Utilisez **R** pour recommencer et **M** pour revenir au menu.

## Organisation des fichiers

```text
java-billiard-game/
├── src/com/billard/
│   └── BilliardGame.java  Tout le code source Java
├── build/classes/         Classes compilees (generees)
├── dist/Billard.jar       Executable JAR (genere)
├── run.bat                Compilation et lancement sous Windows
├── .gitignore
└── README.md
```

Tout le code source du jeu (menu, modes, boules, IA et interface) est reuni dans `src/com/billard/BilliardGame.java`. L'executable est genere separement dans `dist/Billard.jar`.

## Prerequis

Installez un JDK Java (avec `java`, `javac` et `jar` accessibles dans le `PATH`). Java 11 ou plus recent est recommande.

## Compiler et lancer sous Windows

Double-cliquez sur `run.bat`, ou lancez-le depuis PowerShell. Si le terminal est ouvert dans le dossier parent `mini projet` :

```powershell
Set-Location ".\java-billiard-game"
.\run.bat
```

Le script compile les sources, cree `dist/Billard.jar`, puis lance le jeu.

Pour lancer directement l'executable apres la compilation, depuis le dossier `java-billiard-game` :

```powershell
java -jar ".\dist\Billard.jar"
```

Dans PowerShell, utilisez `Set-Location "chemin"` ou `cd "chemin"` pour changer de dossier. La forme `cd /d` est reservee a l'invite de commandes classique.
