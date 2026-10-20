@echo off
rem Recompiles the engine and repackages the jar.
rem Run this after any code change, or the GUI and the bot keep playing the old engine.
setlocal
cd /d "%~dp0"
if exist bin rmdir /s /q bin
javac -d bin src\engine\*.java || exit /b 1
jar --create --file chess-engine.jar --main-class engine.Uci -C bin . || exit /b 1
echo Built chess-engine.jar
endlocal
