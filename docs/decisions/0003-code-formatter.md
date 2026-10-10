# 0003. Code is formatted with palantir-java-format

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The code leans on fluent chains and lambdas (workflows, builders, streams). A formatter decides
how those read more than anything else in the style.

## Decision

Format Java with palantir-java-format (4-space indents, 120 columns), applied by Spotless.

## Consequences

- Fluent chains and lambdas stay compact and readable.
- No style settings to own; `./gradlew spotlessApply` fixes any file.

## Alternatives considered

- **google-java-format:** the best-known option, but 2-space indents and more vertical lambdas.
- **google-java-format, AOSP style:** 4-space indents, but the same, more vertical chain formatting.
- **Eclipse formatter:** fully configurable, but a large config file to maintain.
