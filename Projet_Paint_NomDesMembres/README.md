# Projet Paint — dessin Java internationalisé

## Description

Application de dessin de bureau réalisée en Java Swing. Elle propose une interface en français, anglais et espagnol, avec crayon, gomme, ligne, formes, tampons, pot de peinture et choix des couleurs.

## Fonctionnalités

- Dessiner au crayon, tracer des lignes et créer des rectangles ou des ellipses.
- Utiliser la gomme et les tampons étoile, cœur et flèche.
- Remplir une zone fermée avec le pot de peinture ou remplir les formes.
- Choisir la couleur et l’épaisseur du trait.
- Annuler et rétablir les actions.
- Ouvrir une image et enregistrer le dessin en PNG.
- Changer la langue de l’interface (français, anglais, espagnol).

## Lancement de l’exécutable

Java 17 ou version ultérieure est requis.

Depuis le dossier du projet :

```powershell
java -jar .\executable\Paint.jar
```

Le JAR exécutable fourni se trouve dans `executable/Paint.jar`.

## Compiler depuis les sources

Depuis le dossier du projet, dans PowerShell :

```powershell
$classes = Join-Path $env:TEMP 'ProjetPaintClasses'
New-Item -ItemType Directory -Force $classes
javac -encoding UTF-8 -d $classes .\source\paint.java
Copy-Item .\source\PaintMessages*.properties $classes\
jar --create --file .\executable\Paint.jar --main-class paint -C $classes .
java -jar .\executable\Paint.jar
```

## Structure du projet

```text
Projet_Paint_NomDesMembres/
├── source/
│   ├── paint.java
│   └── PaintMessages*.properties
├── executable/
│   └── Paint.jar
└── README.md
```

## Répartition des tâches

À compléter avec les noms des membres du groupe :

| Membre | Tâches |
|---|---|
| À renseigner | Interface Java Swing et outils de dessin |
| À renseigner | Remplissage, historique et gestion des images |
| À renseigner | Traductions, tests et documentation |
