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
  and blank note forms left out); the text itself is unchanged. Three public-domain pictures
  from Wikimedia Commons were added (figures 11.2, 15.5, 15.6; authors in the captions and in
  `scripts/figures-extra/SOURCE.md`). This material stays under
  CC BY-SA 4.0 with its authorship kept, separate from the GPL-3.0 code.
- `content/src/main/assets/cards.json` (written in `scripts/content/cards.py`) — action cards
  adapted from the same guide: steps shortened and reworded for quick reading. As an
  adaptation they are also under CC BY-SA 4.0, credited to the guide.
- `app/src/main/res/drawable/pict_*.xml` (built by `scripts/pictograms/build.py`) — pictograms for
  card steps, composed from Material Symbols Rounded (Google, Apache License 2.0; the glyphs are in
  `scripts/pictograms/material/` unchanged). Not yet tested with people.
- `app/src/main/res/drawable/om_*.xml` (built by `scripts/pictograms/openmoji.py`) — emoji from OpenMoji
  (https://openmoji.org) by the OpenMoji project, HfG Schwäbisch Gmünd, licensed CC BY-SA 4.0
  (https://creativecommons.org/licenses/by-sa/4.0/); converted to Android vectors, otherwise unchanged;
  the originals are kept in `scripts/pictograms/openmoji/`.
- `app/src/main/res/drawable/ocha_*.xml` (built by `scripts/pictograms/ocha.py`) — OCHA Humanitarian Icons
  by the UN Office for the Coordination of Humanitarian Affairs (https://brand.unocha.org/), freely
  available and listed as public domain (CC0); the originals are kept unchanged in
  `scripts/pictograms/ocha/` with their source. Icons: OCHA.
- `app/src/main/res/drawable/hi_*.xml` (built by `scripts/pictograms/healthicons.py`) — icons from
  Health Icons (https://healthicons.org), CC0 1.0 (public domain); the originals are kept unchanged
  in `scripts/pictograms/healthicons/` with their licence.
- `content/src/main/assets/moz441.json` (built by `scripts/content/moz441.py` from the annex texts in
  `scripts/content/moz441/`) — the 29 first-aid procedures of order No. 441 of the Ministry of Health of
  Ukraine, 09.03.2022 (registered 28.03.2022, No. 356/37692), from moz.gov.ua, word for word. An
  official document, not protected by copyright (Law of Ukraine No. 2811-IX, art. 8).

