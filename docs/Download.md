# Vaultify Download & Run

This guide shows the simplest way to get Vaultify running using the portable launcher scripts. No Java or build tools required.

## Windows
- Download the launcher: [vaultify.bat](../build/scripts/Vaultify.bat) or from Releases: https://github.com/HetMistri/Vaultify/releases/tag/Latest
- Place it anywhere (e.g., Desktop or a folder).
- Double-click `vaultify.bat` to:
  - Create `src/` and `vault_data/` next to the script
  - Download the latest `vaultify.jar` from the "Latest" release
  - Start Vaultify
- To update later, run: [update.bat](../update.bat)

## macOS / Linux
- Download the launcher: [vaultify.sh](../vaultify.sh) or from Releases: https://github.com/HetMistri/Vaultify/releases/tag/Latest
- Open a terminal in the folder where you saved the script, then:
```bash
chmod +x vaultify.sh
./vaultify.sh
```
- This will:
  - Create `src/` and `vault_data/` next to the script
  - Download the latest `vaultify.jar` from the "Latest" release
  - Start Vaultify
- To update later:
```bash
chmod +x update.sh
./update.sh
```

## What gets created
- `src/vaultify.jar`: The portable application jar
- `vault_data/`: Local data folder
  - `db/`, `keys/`, `credentials/`, `certificates/`

## Troubleshooting
- Network issues: If download fails, check your internet connection and try again.
- Permissions (macOS/Linux): Ensure scripts are executable with `chmod +x`.
- Antivirus (Windows): If blocked, allow the script and jar.

## Advanced
- Prefer running from the command line? You can pass Java args via the scripts by editing them, but defaults should work.
- Developers can still build locally using Gradle:
```bash
./gradlew clean build
```
