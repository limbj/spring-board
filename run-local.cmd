@echo off
setlocal
set "SPRING_BOARD_PORT=8080"
if not "%~1"=="" set "SPRING_BOARD_PORT=%~1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-local.ps1" -Port "%SPRING_BOARD_PORT%"
exit /b %ERRORLEVEL%
