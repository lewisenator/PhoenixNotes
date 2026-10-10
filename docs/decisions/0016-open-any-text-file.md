# 0016. Any UTF-8 text file can be opened as the note

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

A notepad that only edits its own hidden file isn't much use. People want to open and edit text
files where they already live.

## Decision

File → Open… opens any text file, and autosave writes back to it. The open file is remembered in
the data folder, so the next start, and a version taking over after an update, open it again.
Only UTF-8 files are opened.

## Consequences

- Edits land in the user's own file, wherever it is.
- The handoff didn't change: the new version reads the remembered file from the data folder.
- Files in other encodings can't be edited.
- If the remembered file is gone, the app opens its own note instead.

## Alternatives considered

- **Guess the encoding:** a wrong guess garbles the file on the next save.
- **Pass the file in the handoff:** works, but the remembered file is needed at startup anyway.
- **Open With / double-click from the desktop:** needs file associations and forwarding files to a
  running copy; left out for now.
