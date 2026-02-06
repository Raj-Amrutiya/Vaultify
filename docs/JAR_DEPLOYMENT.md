# Running Vaultify JAR with .env Configuration

## The Problem

When running the jar file directly (not from IDE), the `.env` file needs to be in the correct location for java-dotenv to find it.

## ✅ Solutions

### Option 1: Place .env in the Same Directory as the JAR (Recommended)

```
my-folder/
├── vaultify.jar
├── .env          ← Your production credentials here
└── run-jar.bat   ← Optional helper script
```

**Steps:**

1. Copy your built jar: `build\libs\vaultify-0.1-beta.jar`
2. Place `.env` file in the same directory
3. Run from that directory:
   ```bash
   cd my-folder
   java -jar vaultify.jar
   ```

### Option 2: Use the Launcher Scripts

We've created helper scripts that ensure everything is in the right place:

**Windows:**

```batch
launcher\run-jar.bat
```

**Linux/Mac:**

```bash
chmod +x launcher/run-jar.sh
./launcher/run-jar.sh
```

### Option 3: Specify Custom .env Path

If you want to keep `.env` in a different location:

```bash
java -Ddotenv.path=/path/to/your/env/folder -jar vaultify.jar
```

Example:

```bash
# Windows
java -Ddotenv.path=C:\config -jar vaultify.jar

# Linux/Mac
java -Ddotenv.path=/etc/vaultify -jar vaultify.jar
```

### Option 4: Use System Environment Variables

For production servers, set environment variables directly:

```bash
# Windows (PowerShell)
$env:DB_URL="jdbc:postgresql://your-host:5432/vaultify"
$env:DB_USER="your_user"
$env:DB_PASSWORD="your_password"
$env:LEDGER_API_URL="https://your-ledger-api.com/api"
java -jar vaultify.jar

# Linux/Mac
export DB_URL="jdbc:postgresql://your-host:5432/vaultify"
export DB_USER="your_user"
export DB_PASSWORD="your_password"
export LEDGER_API_URL="https://your-ledger-api.com/api"
java -jar vaultify.jar
```

## 🔍 How Config Loading Works Now

The updated `Config.java` searches for `.env` in this order:

1. **Custom path** (if `-Ddotenv.path=...` is specified)
2. **Current working directory** (where you run `java -jar` from)
3. **JAR directory** (where the vaultify.jar file is located)
4. **User home directory** (looks for `.vaultify.env` in `~/.vaultify.env`)
5. **Fallback** to config.properties defaults

You'll see a message when the app starts telling you where it found the `.env`:

```
✓ Loaded .env from jar directory: /path/to/jar
```

## 📦 Distribution Package Structure

When distributing your application, include:

```
vaultify-package/
├── vaultify.jar
├── .env.example        ← Template for users
├── run-jar.bat        ← Windows launcher
├── run-jar.sh         ← Linux/Mac launcher
└── README.md          ← Setup instructions
```

## 🚀 Quick Start for End Users

1. Copy `.env.example` to `.env`
2. Edit `.env` with actual credentials
3. Run the launcher script or `java -jar vaultify.jar`

## ❓ Troubleshooting

**Error: "DB connection error"**

- Make sure `.env` is in the same directory as the jar
- Check that `.env` contains `DB_URL`, `DB_USER`, `DB_PASSWORD`
- Verify credentials are correct

**Warning: "No .env file found"**

- This is expected if you want to use only system environment variables
- Or place `.env` file next to the jar

**Still not working?**

- Enable debug: `java -Ddotenv.path=. -jar vaultify.jar` (forces current dir)
- Check the startup message to see where it's looking for .env
- Verify file permissions on .env (should be readable)
