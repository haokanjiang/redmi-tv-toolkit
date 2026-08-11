@echo off
pwsh -NoProfile -File "%~dp0mock-adb.ps1" %*
exit /b %ERRORLEVEL%

