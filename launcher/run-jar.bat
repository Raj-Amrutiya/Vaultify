@echo off
REM Vaultify JAR Launcher with .env support
REM Place this script in the same directory as vaultify.jar and .env

SET JAR_DIR=%~dp0
cd /d "%JAR_DIR%"

echo.
echo ================================================
echo  Vaultify - Secure Credential Manager
echo ================================================
echo.

REM Check if jar exists
if not exist "vaultify.jar" (
    echo [ERROR] vaultify.jar not found in current directory
    echo Expected location: %JAR_DIR%vaultify.jar
    echo.
    pause
    exit /b 1
)

REM Check if .env exists
if exist ".env" (
    echo [OK] Found .env file
) else (
    echo [WARNING] .env file not found
    echo The application will use defaults from config.properties
    echo For production, copy .env.example to .env and configure it
    echo.
)

echo [INFO] Starting Vaultify...
echo.

REM Run the jar (Java will find .env in current directory)
java -jar vaultify.jar

if errorlevel 1 (
    echo.
    echo [ERROR] Application crashed
    pause
)
