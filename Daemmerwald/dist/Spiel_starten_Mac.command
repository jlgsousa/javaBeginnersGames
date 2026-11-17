#!/bin/sh
# Startet "Das Flüstern im Dämmerwald" auf dem Mac (Java 17 oder neuer muss installiert sein)
cd "$(dirname "$0")"
export JAVA_OPTS="-XstartOnFirstThread"
chmod +x bin/daemmerwald
./bin/daemmerwald
