# Jeu de billard Java

Jeu de billard de bureau réalisé en Java Swing, sans dépendance externe.

## Modes de jeu

- Entraînement solo : clic droit pour replacer la boule blanche.
- Deux joueurs.
- Contre l'IA : facile, moyen ou difficile.

Pendant une partie, faites glisser la souris depuis la boule blanche pour viser et régler la puissance du tir. Utilisez **R** pour recommencer et **M** pour revenir au menu.

## Lancer l'exécutable

Un JDK Java est requis. Depuis le dossier `java-billiard-game`, lancez dans PowerShell :

```powershell
java -jar .\executable\Billard.jar
```

Le JAR exécutable fourni se trouve dans `executable/Billard.jar`.

## Compiler depuis les sources

Depuis le dossier `java-billiard-game`, dans PowerShell :

```powershell
$classes = Join-Path $env:TEMP ('JavaBilliardClasses-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes
javac -encoding UTF-8 -d $classes .\source\BilliardGame.java
if ($LASTEXITCODE -ne 0) { throw 'La compilation a échoué.' }
jar --create --file .\executable\Billard.jar --main-class com.billard.BilliardGame -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'La création du JAR a échoué.' }
Remove-Item -LiteralPath $classes -Recurse -Force
java -jar .\executable\Billard.jar
```

## Structure du projet

```text
java-billiard-game/
├── source/
│   └── BilliardGame.java
├── executable/
│   └── Billard.jar
└── README.md
```
