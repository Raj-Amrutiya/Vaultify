#!/usr/bin/env bash
set -euo pipefail
RELEASE_URL="https://github.com/HetMistri/Vaultify/releases/download/Release/vaultify.jar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/src/vaultify.jar"
DATA_DIR="${SCRIPT_DIR}/vault_data"

mkdir -p "${SCRIPT_DIR}/src" "${DATA_DIR}"

if [ ! -f "${JAR_PATH}" ]; then
  echo "[Vaultify] Downloading latest portable jar..."
  if ! curl -fL "${RELEASE_URL}" -o "${JAR_PATH}"; then
    echo "[Vaultify] Download failed. Please check your network." >&2
    exit 1
  fi
fi

echo "[Vaultify] Launching..."
java -jar "${JAR_PATH}"
