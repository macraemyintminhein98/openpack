@echo off
rem OpenPack for Windows. Double-click this file.
cd /d "%~dp0"

set ARCH=amd64
if /I "%PROCESSOR_ARCHITECTURE%"=="x86" if not defined PROCESSOR_ARCHITEW6432 set ARCH=386
if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" set ARCH=arm64

if exist openpack.pub (
  "bin\openpack-windows-%ARCH%.exe" -dir pack -pub openpack.pub %*
) else (
  "bin\openpack-windows-%ARCH%.exe" -dir pack %*
)

if errorlevel 1 (
  echo.
  echo The server did not start. You can still use the library without it:
  echo open the folder "pack" and double-click index.html.
)
pause
