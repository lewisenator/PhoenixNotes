# 0012. Versions are installed side by side and verified before every run

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

The app replaces itself with versions it downloads. A download can be incomplete or tampered with,
and files on disk can change later (disk errors, sync tools, someone editing them).

## Decision

Each version gets its own folder in the per-user data folder, with its jar and the signed manifest
it came with. A `current` file names the version to run and is replaced with an atomic rename. A
version is only installed after its manifest checks out against the current key and the jar
matches it, and only becomes current once it has taken over ([0014](0014-handoff.md)). It's checked
again before every run; if it doesn't verify, the installed app runs itself instead. A version that
fails to take over gets a `failed` file in its folder and isn't tried again.

## Consequences

- Running versions are never overwritten, so Windows' file locks are never a problem.
- A crash leaves either the old version current or the new one, never a broken install.
- A version changed on disk, or another version's files copied in, is caught before it runs.
- Older versions stay on disk; nothing cleans them up yet. They're a few megabytes each.
- No admin rights needed, and uninstalling the app leaves this folder behind.

## Alternatives considered

- **Verify only when downloading:** less work at startup, but trusts the disk forever after.
- **Store only a hash next to each jar:** simpler, but the hash could be changed along with the jar.
