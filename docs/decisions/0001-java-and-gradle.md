# 0001. Java 25 and Gradle

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The app must run on Windows, macOS and Linux, and needs HTTP, signatures, files, processes and a UI.

## Decision

Java 25 (current LTS) and Gradle, with:

- the wrapper's checksum pinned, so a tampered Gradle download is rejected;
- a toolchain, so Gradle downloads Java 25 if it isn't installed;
- one convention plugin (`buildSrc`) for settings every module shares;
- dependency versions in one catalog (`gradle/libs.versions.toml`).

## Consequences

- The JDK covers signatures, HTTP, processes and Swing; libraries fill the rest.
- Each module's build file only says what's specific to it.

## Alternatives considered

- **Maven:** would work; Gradle's convention plugins keep shared setup in one place.
- **Electron or another stack:** its built-in updater would replace what this project builds.
