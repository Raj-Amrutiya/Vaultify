# Vaultify v0.0.1 Beta — Release Notes

**Date:** December 14, 2025
**Version:** v0.0.1-beta

## 🚀 Overview

First public beta of Vaultify, a CLI-based secure credential vault with a tamper-evident audit ledger.

## ✨ Highlights

- AES-256-GCM encryption for stored credentials
- RSA-2048 per-user key pairs
- SHA-256 ledger chaining for audit integrity
- Token-based sharing with revocation and expiry
- Vault CLI with add/list/view/delete flows
- Health, stats, and reconcile/drift checks
- Cross-platform launch scripts (`vaultify.bat`, `vaultify.sh`)

## 🧭 User Commands (non-dev)

- `register`, `login`, `logout`, `whoami`
- `vault` → `add`, `list`, `view`, `delete`, `help`, `back`
- `list-tokens`, `revoke-token`
- `verify-ledger`, `health`, `stats`, `reconcile`, `drift-report`
- `help`, `exit`

## ⚙️ Requirements

- PostgreSQL reachable by the app
- Network access to the ledger server
- Permission to read/write `vault_data`

## 🛠️ Installation

1. Download the release `vaultify` folder
2. Run launcher:
   - Windows: `vaultify.bat`
   - Mac/Linux: `./vaultify.sh` (chmod +x if needed)

## 🔒 Known Limitations

- Developer-only commands are disabled in this beta
- Requires external PostgreSQL and ledger server

## 🐞 Reporting Issues

Report bugs and feedback: https://github.com/HetMistri/Vaultify/issues

## 🙌 Credits

- Het Mistri (Creator)
