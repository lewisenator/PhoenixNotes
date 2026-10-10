# 0004. One program that updates itself

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

A running program has to be replaced by a newer one without disrupting its user, and a bad new
version must not leave the user with nothing that works.

## Decision

One program, with no separate launcher. Each version lives in its own folder. To update, the running
version starts the new one, waits for it to report that it's working, and only then exits. If the
new version doesn't report in time, the old one stops it and keeps running.

## Consequences

- One program and one jar to understand; nothing to install besides the app.
- The update code and trusted keys ship with every release, so key rotations and fixes to the
  updater reach everyone with the next update. With a separate launcher, they live in the part
  that rarely updates, and old launchers fall behind.
- Each version supervises its successor, but only during the handoff. If a new version breaks
  after taking over, nothing rolls it back automatically.
- A release with broken update code can't update itself out of it, so CI tests the update path.
- Running versions are never overwritten, which also avoids Windows' locks on running files.

## Alternatives considered

- **A separate launcher that runs the app as a child process:** can roll back a version that fails
  at any time, but adds a second program, a process protocol and its own packaging.
- **Replace the jar and restart:** simplest, but can't overwrite a running jar on Windows and can't
  recover from a bad version.
- **Existing frameworks (update4j, Conveyor, Sparkle):** would replace what this project shows.
