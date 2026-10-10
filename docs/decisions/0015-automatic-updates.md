# 0015. Updates are checked for regularly and applied straight away

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

A program that updates itself should pick up a release without being asked, but an update
shouldn't surprise anyone or lose their work.

## Decision

The app checks at startup, every 15 minutes, and from Help → Check for Updates. A newer release is
downloaded, verified and handed over to straight away, with no prompt. While it happens, the window
is dimmed with "Updating to X…" and typing is paused; the new version shows "Updated to X", and a
failure shows "Update to X failed — still on Y".

## Consequences

- A release reaches running copies within 15 minutes, or at once from the menu.
- Nothing typed is lost: the note is saved first, and a failed save stops the update.
- Updates can arrive mid-sentence. The pause is a few seconds, and it's impossible to miss.
- Checks run on a background thread, so the window never waits on the network.

## Alternatives considered

- **Ask first:** gentler, but most people click "Later" and stay on old versions.
- **Apply on next start:** no interruption, but slower to reach people, and harder to demo.
