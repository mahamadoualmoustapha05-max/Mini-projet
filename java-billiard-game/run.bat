@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo Erreur : Java JDK est requis. Installez un JDK puis reessayez.
    pause
    exit /b 1
)

if not exist build\classes mkdir build\classes
if not exist dist mkdir dist

javac -encoding UTF-8 -d build\classes src\com\billard\BilliardGame.java
if errorlevel 1 (
    echo Erreur pendant la compilation.
    pause
    exit /b 1
)

jar --create --file dist\Billard.jar --main-class com.billard.BilliardGame -C build\classes .
if errorlevel 1 (
    echo Erreur pendant la creation de l'executable JAR.
    pause
    exit /b 1
)

echo Compilation terminee : dist\Billard.jar
java -jar dist\Billard.jar
if errorlevel 1 (
    echo Le jeu ne peut pas demarrer. Verifiez votre installation Java.
    pause
    exit /b 1
)

endlocal
