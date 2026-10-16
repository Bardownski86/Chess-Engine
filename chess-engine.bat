@echo off
rem Launcher for UCI chess GUIs (Arena, Cute Chess, En Croissant, Banksia).
rem Point the GUI at this file as the engine executable.
java -cp "%~dp0bin" engine.Uci
