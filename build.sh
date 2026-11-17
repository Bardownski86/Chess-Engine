#!/bin/sh
# Mac/Linux version of build.bat. Recompiles the engine and repackages the jar.
set -e
cd "$(dirname "$0")"
rm -rf bin
javac -d bin src/engine/*.java
jar --create --file chess-engine.jar --main-class engine.Uci -C bin .
echo "Built chess-engine.jar"
