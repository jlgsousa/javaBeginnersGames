@echo off
REM Startet "Das Flüstern im Dämmerwald" (benötigt Java 17+)
cd /d "%~dp0"
call gradlew.bat run
