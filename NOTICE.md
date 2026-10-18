# Notice

**AIMeter** (called “AI Usage Widget” up to version 1.0.2) is a modified version of **CodexMeter**.

- Original project: <https://github.com/KyoMio/CodexMeter>
- Base commit: `2f7bf52167967aab97f7cb1f1e7d9af7ac3a9977` (tag `v0.2.4`, 2026-10-08)
- Original copyright: Copyright (c) 2026 KyoMio
- Original license: MIT License — the full text is kept unchanged in [`LICENSE`](LICENSE) and
  is also shown inside the app (Settings → About → Licenses).

The git history of this repository starts with the unmodified upstream history up to the base
commit, so every change made for AIMeter can be reviewed with
`git diff 2f7bf52167967aab97f7cb1f1e7d9af7ac3a9977..HEAD`.

Modifications for AIMeter are released under the same MIT License. The AIMeter icon
(`app/src/main/res/drawable/ic_launcher_foreground.xml`, `assets/brand/`) was made for this fork;
CodexMeter's icon and logo are not used.

## Third-party font

The app bundles the **Geist** typeface (`app/src/main/res/font/geist_variable.ttf`),
Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font), licensed under the
SIL Open Font License, Version 1.1. The license is kept in
[`licenses/Geist-OFL.txt`](licenses/Geist-OFL.txt) and is also shown inside the app
(Settings → About → Licenses).

## Trademarks

AIMeter and CodexMeter are unofficial projects. They are not affiliated with, endorsed by or
sponsored by OpenAI or Anthropic. "Codex", "ChatGPT", "OpenAI", "Claude" and "Anthropic" are
trademarks of their respective owners and are used only to identify the services whose usage
limits the app displays.

Parsing of some usage responses in the original project references
[steipete/CodexBar](https://github.com/steipete/CodexBar).
