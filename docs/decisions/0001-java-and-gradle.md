# 0001. Java 25 and Gradle

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The app must run on Windows, macOS and Linux, and needs HTTP, signatures, files, processes and a UI.

## Decision

Java 25 (current LTS) and Gradle, with:

- the wrapper's checksum pinned, so a tampered Gradle download is rejected;
- a toolchain, so Gradle downloads Java 25 if it isn't installed;
- each module's build file self-contained, repeating the few lines both modules share;
- dependency versions in one catalog (`gradle/libs.versions.toml`).

## Consequences

- The JDK covers signatures, HTTP, processes and Swing; libraries fill the rest.
- Each build file can be read on its own. Shared settings (Java version, tests, quality checks)
  appear in both, so a change to them is made twice.

## Alternatives considered

- **Maven:** would work just as well.
- **A convention plugin in `buildSrc`:** shared settings in one place, but a special folder and
  plugin to explain, for two modules' worth of duplication.
- **Electron or another stack:** its built-in updater would replace what this project builds.
