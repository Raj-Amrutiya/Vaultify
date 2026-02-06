@echo off
setlocal enableextensions
set REPO_OWNER=HetMistri
set REPO_NAME=Vaultify
set RELEASE_URL=https://github.com/HetMistri/Vaultify/releases/download/Latest/vaultify.jar
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%src\vaultify.jar
set DATA_DIR=%SCRIPT_DIR%vault_data

if not exist "%SCRIPT_DIR%src" mkdir "%SCRIPT_DIR%src"
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

echo [Vaultify] Updating to latest portable jar...
powershell -Command "try { Invoke-WebRequest -Uri '%RELEASE_URL%' -OutFile '%JAR_PATH%' -UseBasicParsing } catch { exit 1 }"
if errorlevel 1 (
    echo [Vaultify] Update failed. Please check your network.
    exit /b 1
)

echo [Vaultify] Update complete. Launch with vaultify.bat
endlocal
