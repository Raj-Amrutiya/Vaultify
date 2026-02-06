#!/bin/bash
# Vaultify JAR Launcher with .env support
# Place this script in the same directory as vaultify.jar and .env

JAR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$JAR_DIR"

echo ""
echo "================================================"
echo " Vaultify - Secure Credential Manager"
echo "================================================"
echo ""

# Check if jar exists
if [ ! -f "vaultify.jar" ]; then
    echo "[ERROR] vaultify.jar not found in current directory"
    echo "Expected location: $JAR_DIR/vaultify.jar"
    echo ""
    exit 1
fi

# Check if .env exists
if [ -f ".env" ]; then
    echo "[OK] Found .env file"
else
    echo "[WARNING] .env file not found"
    echo "The application will use defaults from config.properties"
    echo "For production, copy .env.example to .env and configure it"
    echo ""
fi

echo "[INFO] Starting Vaultify..."
echo ""

# Run the jar (Java will find .env in current directory)
java -jar vaultify.jar

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Application crashed"
fi
