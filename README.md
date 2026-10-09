# Phoenix Notes

A desktop notepad that keeps itself up to date. It downloads signed releases, verifies them, and
hands over to the new version while it's running, falling back to the old one if the new one fails.

An exercise in safe, seamless self-updating software.

**Status:** work in progress. Design decisions are in [docs/decisions](docs/decisions/README.md).

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
