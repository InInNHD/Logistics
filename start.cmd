@echo off
setlocal
title Firefly Logistics - Start
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" -DataMode Demo -AllowDevDefaults
set "FF_EXIT=%ERRORLEVEL%"
if not "%FF_EXIT%"=="0" (
  echo.
  echo Startup did not complete. Review the message above and retry.
  pause
)
exit /b %FF_EXIT%
