# Phoenix Notes

A desktop notepad that keeps itself up to date. It downloads signed releases, verifies them, and
hands over to the new version while it's running, falling back to the old one if the new one fails.

An exercise in safe, seamless self-updating software.

**Status:** work in progress. Design decisions are in [docs/decisions](docs/decisions/README.md).

## Installing

Download the installer for your computer from the
[latest release](https://github.com/lewisenator/PhoenixNotes/releases/latest). Each one includes
its own Java, and installs for you alone, without admin rights (except on Linux). After that the
app keeps itself up to date: an old installer always starts the newest version it has downloaded.

| OS | File | Install | Uninstall |
|----|------|---------|-----------|
| macOS (Apple silicon) | `Phoenix Notes-<version>.dmg` | Open it and drag the app to Applications. | Move the app to the Bin. |
| Windows | `Phoenix Notes-<version>.msi` | Run it. It adds a Start menu entry and a desktop shortcut. | Settings → Apps. |
| Linux (Debian, Ubuntu) | `phoenix-notes_<version>_amd64.deb` | `sudo apt install ./phoenix-notes_*.deb` | `sudo apt remove phoenix-notes` |

The installers aren't signed by Apple or Microsoft, so the first launch needs one extra step:

- **macOS:** it says the app can't be checked for malware. Open *System Settings → Privacy &
  Security*, and click *Open Anyway* next to Phoenix Notes.
- **Windows:** SmartScreen says it protected your PC. Click *More info*, then *Run anyway*.

Updates themselves are always checked against the app's signing keys, so these warnings are about
the installer only ([decision 0017](docs/decisions/0017-installers.md)).

**Anywhere else** (an Intel Mac, say), install Java 25 and run the release's jar:
`java -jar app-<version>.jar`. It updates itself the same way.

Uninstalling leaves your note and downloaded versions behind, in
`~/Library/Application Support/PhoenixNotes` (macOS), `%LOCALAPPDATA%\PhoenixNotes` (Windows) or
`~/.local/share/phoenixnotes` (Linux). Files you opened are never touched.

## Layout

| Module     | What it is                                                           |
|------------|----------------------------------------------------------------------|
| `app/`     | The notepad, and everything it needs to update itself               |
| `signing/` | Release manifests and signing keys, plus a command-line tool for CI |

## Building

Needs a JDK 17 or newer to run Gradle; the build downloads Java 25 if it isn't installed.

```bash
./gradlew build
```

To build the installer for the computer you're on (into `app/build/installer`):

```bash
./gradlew :app:installer
```

## Signing keys

Releases are signed with keys that can be rotated, and replaced in an emergency, without
reinstalling anything ([decision 0009](docs/decisions/0009-key-rotation.md)):

| Key | Private key lives in | Signs |
|-----|----------------------|-------|
| **current** | GitHub secret `PHOENIXNOTES_SIGNING_KEY` | Every release |
| **next** | 1Password | The rotation that makes it current |
| **break glass** | 1Password | An emergency replacement of every key |

The public keys are in [`trust/keys.json`](trust/keys.json), a chain where each generation is
signed by a key the one before it trusts. It's built into the app and published with every
release.

| Command | When | What it does |
|---------|------|--------------|
| `./gradlew keysInit` | Once | Creates all three keys and generation 0. Refuses if any keys exist. |
| `./gradlew keysRotate` | Every few months, or if the CI key may have leaked | Next becomes current (in CI); a new next is created. Installed versions stay trusted. |
| `./gradlew keysBreakGlass` | If the next key is lost or may have leaked | Replaces all three keys; apps download their version again. Asks for confirmation. |

Private keys never touch the disk or appear in command arguments.

### Before you start

1. **Install the 1Password and GitHub command-line tools:**
   - macOS: `brew install 1password-cli gh`
   - Windows: `winget install AgileBits.1Password.CLI GitHub.cli`
   - Linux: see [1Password CLI](https://developer.1password.com/docs/cli/get-started/) and
     [GitHub CLI](https://github.com/cli/cli#installation)
2. **Sign in to 1Password:** in the 1Password app, turn on *Settings → Developer → Integrate with
   1Password CLI*. Check with `op vault list`. Keys go in the `Private` vault; to use another, set
   `PHOENIXNOTES_OP_VAULT`, e.g. `PHOENIXNOTES_OP_VAULT=Work ./gradlew keysRotate`.
3. **Sign in to GitHub** with an account that can manage this repository's secrets:
   `gh auth login`. Check with `gh secret list`.
4. **Run the commands from the repository root, on an up-to-date `main`.** On Windows, use
   `gradlew.bat` instead of `./gradlew`.

### Setting up the keys (once)

```bash
./gradlew keysInit
git add trust/keys.json && git commit -m "Signing keys" && git push
```

### Rotating the keys

```bash
git pull
./gradlew keysRotate
git commit -am "Rotate signing keys" && git push
```

### Replacing every key (break glass)

```bash
git pull
./gradlew keysBreakGlass      # type "break glass" when asked
git commit -am "Replace signing keys" && git push
```

After each one, **push `trust/keys.json` straight away**: until it's on `main`, releases fail CI's
check rather than shipping with a key apps don't trust yet.

**If a command fails partway:** each one checks everything before changing anything, and replaced
1Password items are archived, not deleted. Restore anything archived from 1Password's Archive,
discard the `trust/keys.json` change (`git checkout -- trust/keys.json`), and run it again.