# Third-party material

- SQLCipher for Android (Zetetic), AndroidX, Jetpack Compose, Material Components and Kotlin
  libraries are used as dependencies under their own licences.
- `content/src/main/assets/guide.json` and `content/src/main/assets/figures/` — the open
  "Довідник виживання" 2.3 (https://github.com/snegireff/dovidnyk-vyzhyvannia), licensed under
  CC BY-SA 4.0. Changes: converted for display in the app by `scripts/build_guide.py` (markup
  changed, figures rendered as pictures by `scripts/render_figures.js`, cover, table of contents
  and blank note forms left out); the text itself is unchanged. This material stays under
  CC BY-SA 4.0 with its authorship kept, separate from the GPL-3.0 code.
- `content/src/main/assets/cards.json` (written in `scripts/content/cards.py`) — action cards
  adapted from the same guide: steps shortened and reworded for quick reading. As an
  adaptation they are also under CC BY-SA 4.0, credited to the guide.
