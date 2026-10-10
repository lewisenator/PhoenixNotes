# 0011. Every merge to main is a release, numbered by commit count

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

Apps need to tell whether a release is newer than what they're running. Nobody chooses which
version to install: apps always update to the latest.

## Decision

Every push to `main` that passes CI is released as `1.0.<number of commits on main>`. Apps compare
versions by the number after the last dot. Docs-only pushes don't release.

## Consequences

- Comparing versions is one integer comparison, with no parsing library or pre-release rules.
- The next version is predictable, and changing the `1.0.` prefix later doesn't affect ordering.
- `main` must never be force-pushed, or the count could repeat and collide with an existing release.
- Versions can skip numbers (docs-only commits, close-together pushes); only the order matters.
- Compatibility has to be stated explicitly (e.g. a minimum version), not implied by a major version.

## Alternatives considered

- **Semantic versioning:** tells people who choose when to upgrade whether a version breaks things;
  nobody chooses here.
- **CI's run number:** also increases, but isn't tied to the code: re-running a commit gives a new number.
