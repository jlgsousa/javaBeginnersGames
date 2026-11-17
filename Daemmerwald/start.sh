#!/usr/bin/env sh
# Startet "Das Flüstern im Dämmerwald" (benötigt Java 17+; lädt beim ersten Start Gradle und die 3D-Engine herunter)
cd "$(dirname "$0")"
./gradlew run
