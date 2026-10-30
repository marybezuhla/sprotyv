# Third-party material

- SQLCipher for Android (Zetetic), AndroidX, Jetpack Compose, Material Components and Kotlin
  libraries are used as dependencies under their own licences.
- Bouncy Castle (https://www.bouncycastle.org), MIT-style licence, provides Argon2id for
  password-protected backups and the family plan code.
- `app/src/main/assets/eff_large_wordlist.txt` — the EFF Long Wordlist by the Electronic
  Frontier Foundation (https://www.eff.org/dice), licensed under CC BY 3.0 US. Used unchanged
  (dice numbers removed) to suggest passwords.
- ZXing core (https://github.com/zxing/zxing), Apache License 2.0, makes and reads QR codes on
  the phone.
- `content/src/main/assets/guide.json` and `content/src/main/assets/figures/` — the open
  "Довідник виживання" 2.3 (https://github.com/snegireff/dovidnyk-vyzhyvannia), licensed under
  CC BY-SA 4.0. Changes: converted for display in the app by `scripts/build_guide.py` (markup
  changed, figures rendered as pictures by `scripts/render_figures.js`, cover, table of contents
  and blank note forms left out); the text itself is unchanged. This material stays under
  CC BY-SA 4.0 with its authorship kept, separate from the GPL-3.0 code.
- `content/src/main/assets/cards.json` (written in `scripts/content/cards.py`) — action cards
  adapted from the same guide: steps shortened and reworded for quick reading. As an
  adaptation they are also under CC BY-SA 4.0, credited to the guide.
- `app/src/main/res/drawable/pict_*.xml` (built by `scripts/pictograms/build.py`) — pictograms for
  card steps, composed from Material Symbols Rounded (Google, Apache License 2.0; the glyphs are in
  `scripts/pictograms/material/` unchanged). Not yet tested with people.
