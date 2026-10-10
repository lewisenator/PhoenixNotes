# 0007. Public GitHub repository for code and releases

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The app needs somewhere to download releases from, and CI needs to build on three operating
systems. Private repositories need a token to download releases and have limited CI minutes.

## Decision

One public repository for code and releases. The app downloads releases anonymously; trust comes
from the release signature, not the host.

## Consequences

- No credentials in the app, and free CI on all three operating systems.
- Everything committed is public, so private keys never go in the repository.

## Alternatives considered

- **A token in the app:** easy to extract, and it would expose the code.
- **Private code, public releases repository:** fine if the code must stay private.
- **Object storage or an update server:** better at scale or for restricting access; not needed here.
