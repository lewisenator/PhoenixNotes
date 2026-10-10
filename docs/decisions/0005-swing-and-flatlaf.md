# 0005. The UI is Swing with FlatLaf

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

The app needs a simple cross-platform window. The project is about updating, not UI.

## Decision

A Swing window, styled with FlatLaf for a modern look.

## Consequences

- Swing is part of the JDK: no web server, browser, tray icon or native libraries.
- FlatLaf is one dependency and one line of setup.
- Swing code is awkward to unit test, so window classes stay thin and logic lives in plain classes.

## Alternatives considered

- **A browser UI with a tray icon:** needs an HTTP server, web files, a tray icon to quit, and the
  port handed over during an update.
- **JavaFX with an embedded web view:** native libraries per OS and a JavaScript-to-Java bridge.
