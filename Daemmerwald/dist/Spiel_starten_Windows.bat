@echo off
REM Startet "Das Flüstern im Dämmerwald" unter Windows (Java 17 oder neuer muss installiert sein)
cd /d "%~dp0"
call bin\daemmerwald.bat
if errorlevel 1 (
  echo.
  echo Das Spiel konnte nicht starten. Ist Java 17 oder neuer installiert?  https://adoptium.net
  pause
)
