@echo off
setlocal
title Firefly Logistics - Stop
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop.ps1"
set "FF_EXIT=%ERRORLEVEL%"
if not "%FF_EXIT%"=="0" pause
exit /b %FF_EXIT%
