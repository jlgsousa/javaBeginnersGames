#!/usr/bin/env sh
# Kompiliert und startet "Das Flüstern im Dämmerwald" (benötigt Java 17+)
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out src/daemmerwald/*.java && java -cp out daemmerwald.Main
