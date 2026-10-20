@echo off
rem UCI engine launcher for chess GUIs and for lichess-bot.
rem Runs the self-contained jar, so it cannot pick up a stale class file.
rem If the jar is missing, run build.bat first.
java -jar "%~dp0chess-engine.jar" %*
