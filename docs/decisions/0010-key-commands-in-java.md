# 0010. Key commands are written in Java, not shell scripts

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

Setting up, rotating and replacing keys means generating keys, signing `keys.json`, and calling the
1Password (`op`) and GitHub (`gh`) command-line tools in a safe order.

## Decision

They're commands in the `signing` tool, run as `./gradlew keysInit`, `keysRotate` and
`keysBreakGlass`. `op`, `gh` and `git` are called through a small `Shell` interface, behind
`Vault` (1Password) and `CiSecrets` (GitHub).

## Consequences

- One language, and the commands reuse `KeyChain` directly.
- Tests replace 1Password, GitHub and git with in-memory fakes, and check that private keys only
  ever go to those tools on stdin.
- Works on Windows without a Unix shell.
- About 40 lines of process plumbing that a shell script gets for free.

## Alternatives considered

- **Bash scripts:** shorter to write, but a second language, harder to test (fake commands on the
  `PATH`), and awkward on Windows.
- **JBang scripts:** single Java files with their own dependencies, but one more tool to install.
