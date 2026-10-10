# 0014. The running version hands over to the new one over stdin and stdout

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

With no launcher (0004), the running version has to start the new one and get out of the way,
without losing the note, and without leaving a broken version in charge if the new one fails.

## Decision

Old starts new as a child process with `--handoff`, and they talk over new's stdin and stdout:

1. New says `ready`.
2. Old saves the note, releases the data-folder lock, and sends `go x y w h` (its window's
   position), or just `go` when it has no window.
3. New takes the lock, opens its window there, and says `running`.
4. Old makes new current and exits.

If new doesn't say `ready` within 30 seconds or `running` within 15, or exits first, old stops it,
takes the lock back, marks that version as failed, and carries on. An older install that starts
up and finds a newer verified version current hands over the same way.

## Consequences

- `current` only changes once new is running, so a failed update never leaves a broken version
  current, and a failed version isn't retried until there's a newer one.
- The window reopens where it was, with what was typed, so an update looks like a refresh.
- The messages are a contract between versions: once released, changes must stay compatible.
- It runs with a real child process in CI, since a release with broken handoff code can't fix
  itself.

## Alternatives considered

- **A local port or file:** other programs could join in; a pipe only connects the two processes.
- **Exit, then let the new version start alone:** no way to fall back if it doesn't start.
