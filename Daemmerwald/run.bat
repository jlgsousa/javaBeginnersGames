@echo off
REM Kompiliert und startet "Das Flüstern im Dämmerwald" (benötigt Java 17+)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 -d out src\daemmerwald\*.java && java -cp out daemmerwald.Main
