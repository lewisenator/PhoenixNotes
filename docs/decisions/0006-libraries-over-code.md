# 0006. Prefer libraries to hand-written code

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

Every line is something to read, test and explain. The interesting parts are signing, verifying and
handing over between versions; the rest is plumbing.

## Decision

Use well-known libraries for plumbing: picocli (command line), Lombok (boilerplate), Jackson (JSON),
FlatLaf (UI look). Write by hand only what shows how updating works.

## Consequences

- Less code around the parts that matter.
- A bigger download: each release ships its libraries. A few megabytes is fine here.
- Each library is one more dependency to keep up to date (Dependabot opens the pull requests).

## Alternatives considered

- **Only the JDK:** smaller downloads, but more hand-written parsing, argument handling and boilerplate.
