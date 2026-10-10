# 0017. Every release has an unsigned installer for each OS, with its own Java

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

Asking people to install Java 25 and run a jar is a lot to ask. But the app updates itself, so the
installer only has to get the first version onto a computer.

## Decision

CI builds a `.dmg`, an `.msi` and a `.deb` with `jpackage` for every release, each on its own OS,
and adds them to the release. Each includes a Java runtime with just the modules the app uses, and
keeps `bin/java`, which a handoff uses to start the next version. Windows installs per user; macOS
installs wherever the app is dragged. The installers aren't signed or notarized.

## Consequences

- One download, no Java to install, no admin rights on macOS and Windows.
- An installed app never changes; updates go in the data folder, and an old installer starts the
  newest version it has (verified first), costing an extra second at launch.
- macOS and Windows warn on first launch, and the README explains the extra click. Trust in
  updates doesn't depend on this: they're checked against the app's own keys.
- The macOS installer is for Apple silicon only; other computers can run the jar.
- Each release adds about 100 MB of installers, so old releases should be pruned now and then.

## Alternatives considered

- **Sign and notarize:** no warnings, but needs paid Apple and Microsoft certificates.
- **Jar only:** smallest, but people need Java 25 first.
- **Update the installed app in place:** no extra launch, but needs write access to the install
  folder (often admin rights) and skips verifying before every run.
