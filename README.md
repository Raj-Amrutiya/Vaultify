# Vaultify v0.1 Beta — Secure Credential Vault System

Vaultify is a CLI-based secure credential vault that encrypts your secrets locally and records all sensitive actions in a tamper-evident remote audit ledger.

It is designed to demonstrate secure system design, modern cryptography, and real-world architecture concepts—while remaining practical and usable.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-blue.svg)](https://gradle.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)

---

## 📋 Table of Contents

- 🚀 What Vaultify Does
- 🧠 How It Works (Simple Explanation)
- 📦 Download & Run
- 🧭 Main Commands (Non-Developer Mode)
- 🧪 Typical First-Time Usage
- ⚙️ Requirements
- 🛡️ Security Notes (In Plain English)
- ✨ Features
- 🔒 Security (Explain Crypto)
- 👤 Author
- 🐞 Report Issues
- 🙌 Thank You for Using Vaultify

---

# 🚀 What Vaultify Does

Vaultify allows you to:

✅ Store files and secrets encrypted on your local machine

✅ Manage credentials through a simple command-line interface

✅ Share credentials using time-limited access tokens

✅ Detect tampering using a blockchain-style audit ledger

✅ Verify credential integrity across different machines


# 🧠 How Vaultify Works

At a high level:

🔒 Credentials are encrypted using AES-256-GCM

🔑 Each user has a unique RSA-2048 key pair

📜 Every sensitive action is logged to a remote ledger

🔍 Integrity can be verified at any time

You do not need cryptography knowledge—Vaultify handles this internally.

# 📦 Download & Run
Step 1 — Download Launcher

Visit the Releases page on GitHub or https://github.com/HetMistri/Vaultify/releases/tag/Launcher

Download the latest vaultify launcher

Step 2 — Launch

Windows

```sh
vaultify.bat
```

Mac / Linux

```sh
./vaultify.sh
```

(If needed: `chmod +x vaultify.sh`)

You will see:

```sh
vaultify>
```

This is the Vaultify command prompt.

# 🧭 Command Overview
👤 Account Commands

**register** — Create a new user account
(Automatically generates encryption keys)

**login** — Log in to your account

**logout** — End the current session

**whoami** — Display the active user

🗄️ Vault Commands

Enter the vault:

```sh
vault
```

Available inside:

- **add** — Encrypt and store a credential
- **list** — List stored credentials
- **view** — Decrypt and view a credential
- **delete** — Delete a credential you own
- **help** — Show vault commands
- **back** — Exit the vault

🔑 Token & Access Control

**list-tokens** — View issued access tokens

**revoke-token** — Revoke a previously issued token

🔍 Verification & Health

**verify-ledger** — Verify audit ledger integrity

**health** — Check system health (storage, ledger connectivity)

**stats** — View usage statistics

**reconcile / drift-report** — Detect inconsistencies between local and remote state

ℹ️ General

**help** — Display available commands

**exit** — Exit Vaultify

# 🧪 First-Time Usage Guide

Start Vaultify

Run `register`

Run `login`

Enter `vault`

Add credentials using `add`

Use `list`, `view`, or `delete`

Manage access with `list-tokens` / `revoke-token`

Verify integrity using `verify-ledger`

# ✨ Features

🔐 Local encryption using AES-256-GCM

🔑 Per-user RSA-2048 key pairs

🔗 Tamper-evident remote audit ledger (SHA-256 hash chain)

⏱️ Token-based credential sharing with expiry & revocation

🧪 Health checks, statistics, and drift detection

🖥️ Cross-platform support (Windows, macOS, Linux)

🧠 Clean, layered architecture (CLI → Service → Crypto → Ledger)

# 🔒 Security Design

**AES-256-GCM:** Provides confidentiality and integrity for stored data

**RSA-2048:** Used to wrap AES encryption keys per user

**SHA-256:** Hashes actions for ledger integrity

**Encrypted Private Keys:** Private keys are encrypted at rest and unlocked only during login

**Time-Limited Tokens:** Tokens expire automatically and can be revoked

**Ledger Verification:** Detects unauthorized changes or rollback attempts

# ⚙️ Requirements

To run Vaultify:

🌐 Network access to the remote ledger server

📁 Permission to read/write the `vault_data` directory

Configuration is handled through the provided config file in the release.

# 👤 Author

Het Mistri

🔗 LinkedIn:
https://www.linkedin.com/in/het-mistri-7a52a533a/

💻 GitHub:
https://github.com/HetMistri

# 🐞 Reporting Issues

If you encounter bugs or unclear behavior:

📌 Report issues here:
https://github.com/HetMistri/Vaultify/issues

🙌 Thank You for Using Vaultify

Vaultify is built as an academic and architectural showcase of secure systems, cryptography, and real-world software design.

Happy vaulting 🔐
