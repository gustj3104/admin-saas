@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0convert-to-hwp.ps1" "%~1" "%~2" "%~3"
exit /b %ERRORLEVEL%
