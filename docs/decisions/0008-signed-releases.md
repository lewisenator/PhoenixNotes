# 0008. Releases are described by a signed manifest

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The app downloads code from the internet and runs it. It has to know the code came from us and
wasn't changed on the way, wherever it's hosted.

## Decision

Each release has a `manifest.json` (version, jar URL, SHA-256, size) and a detached Ed25519
signature over its exact bytes. The app checks the signature before parsing anything, then checks
the downloaded jar's size and hash against the manifest. The only way to get a parsed release is
through that check.

## Consequences

- Hosts don't need to be trusted; only our keys do.
- Signing raw bytes means signer and app never have to produce identical JSON.
- The JDK supports Ed25519 natively; Jackson is the only extra dependency.
- Manifest fields are validated while parsing, so a signed but nonsensical manifest is rejected.
  The version becomes a folder name, so it's digits and dots only.

## Alternatives considered

- **A signed JWT:** one file with built-in expiry, but JWT libraries have a history of
  algorithm-confusion bugs, and it's built for auth tokens.
- **Signed jars (`jarsigner`):** built into Java, but verifying them correctly is subtle.
- **TUF or Sigstore:** stronger guarantees, but much heavier than this project needs.
