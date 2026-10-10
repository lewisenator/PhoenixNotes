# 0002. Quality checks run on every build

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The code has to stay correct and consistent while it changes quickly, including live in a review.

## Decision

`./gradlew check`, run locally and in CI on all three OSes, fails on:

- **formatting** that doesn't match the formatter ([0003](0003-code-formatter.md); `./gradlew spotlessApply` fixes it);
- **bug patterns** found by Error Prone at compile time;
- **any compiler warning** (`-Xlint:all -Werror`), except `serial`: nothing is ever serialized, so
  it would only demand boilerplate IDs on every exception class;
- **line coverage** below 90% in `signing` and 70% in `app`.

## Consequences

- Style never comes up in review, and common bugs fail the build instead of shipping.
- `app` has a lower bar because its Swing classes are thin and lightly tested; logic lives in
  plain classes with normal tests.
- Every task ships with tests, or the build fails.

## Alternatives considered

- **Checkstyle, PMD, SpotBugs:** overlap with the formatter and Error Prone, with more noise.
