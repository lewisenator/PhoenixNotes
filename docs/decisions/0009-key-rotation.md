# 0009. Signing keys rotate by pre-rotation, with a break-glass key

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

With one fixed signing key, a leak can never be undone: every app trusts that key forever. And
GitHub secrets can't be read back, so anything that signs a rotation can't live only in CI.

## Decision

Three keys, published as a chain of generations in `trust/keys.json` and built into every app:

- **current** signs releases. Its private key is only in CI.
- **next** is committed to in advance. Its private key is only in 1Password; it signs the rotation
  that makes it current, so each next key is used exactly once.
- **break glass** is for emergencies. Its private key is only in 1Password; it can sign a
  generation that replaces every key.

An app accepts a new generation only if the previous one vouches for it:

1. **Rotation:** signed by the previous next key, promotes that key, and leaves break glass alone.
2. **Break glass:** signed by the previous break-glass key; may change everything.

New releases must be signed by the latest current key. Installed versions are trusted if signed by
any current key since the last break glass. Generation 0 is signed once, locally, when the keys are
set up; apps trust it because it's built into them, not because of its signature.

## Consequences

- A leaked CI key gives an attacker a window, not the fleet: they can't add keys, and the next
  rotation revokes theirs. Installed versions stay trusted through routine rotations.
- If the next key is lost or leaks, break glass recovers, distrusting every older version at once.
- The most powerful key stays in 1Password except in an emergency.
- Apps that missed rotations catch up from one download: every `keys.json` holds the whole chain.
- Rotating needs a person with 1Password access; it isn't automated.
- A machine that installed a malicious release during a compromise may stay compromised; no update
  system can undo code that already ran.

## Alternatives considered

- **Two keys: CI's current key plus an offline root that signs every rotation** (TUF's model): one
  key fewer, but the root comes out for every rotation, and a CI leak isn't a cheap fix.
- **Rotations endorsed by the current key:** could be automated in CI, but a leaked current key
  could then endorse an attacker's key.
- **A separate list of revoked keys:** duplicates what the chain already says.
