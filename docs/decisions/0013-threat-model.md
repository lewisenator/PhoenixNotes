# 0013. What the update mechanism defends against

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

Security choices need a stated boundary, or every option looks either necessary or pointless.

## Decision

The app must never run code that wasn't signed with our keys, whatever happens between CI and the
user's machine: a compromised or impersonated download server, a tampered network, a corrupted
download, or a damaged data folder.

It doesn't defend against other programs running as the same OS user. Those can already run any
code they like as that user, including editing the installed app itself.

## Consequences

- Trust comes from signatures, never from the host, the URL or the data folder. Moving releases
  to another host changes nothing about security.
- A leaked signing key is handled by rotation ([0009](0009-key-rotation.md)): a window, not the fleet.
- Malware running as the user is the operating system's problem, not this app's.
- The first install trusts the downloaded installer; signed installers would harden that.

## Alternatives considered

- **Also defend against programs running as the same user:** needs OS-level protections
  (admin-owned install folders, sandboxed signed apps) this project can't provide on its own.
