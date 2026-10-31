#!/usr/bin/env python3
"""
The Constitution of Ukraine in full, as one JSON file for the app: the preamble, the sections and
every article word for word, with the editorial notes of the official edition kept apart.

Input: constitution/constitution-2020-01-01.txt, the text of the current edition on the
Verkhovna Rada's site (zakon.rada.gov.ua/laws/show/254к/96-вр, "Редакція від 01.01.2020"),
kept unchanged. An official document: not protected by copyright; the site's content is also
under CC BY 4.0. Only the layout is read from it, nothing is reworded.

    python3 scripts/content/constitution.py  ->  content/src/main/assets/constitution.json
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "constitution", "constitution-2020-01-01.txt")
OUT = os.path.join(HERE, "..", "..", "content", "src", "main", "assets", "constitution.json")

SECTION = re.compile(r"^Розділ ([IVXL]+)$")
ARTICLE = re.compile(r"^Стаття (\d{1,3}(?:-\d)?)\.\s*(.*)$")


def norm(s):
    return re.sub(r"[\s​‎   ]+", " ", s).strip()


def main():
    lines = [norm(l) for l in open(SRC, encoding="utf-8").read().split("\n")]
    # Everything before the preamble is the edition's list of amendments and court decisions
    start = next(i for i, l in enumerate(lines) if l.startswith("Верховна Рада України від імені Українського народу"))
    preamble, sections, closing = [], [], []
    current_section, current_article = None, None
    i = start
    note = None
    while i < len(lines):
        l = lines[i]
        i += 1
        if not l:
            continue
        # Editorial notes in braces, sometimes over several lines
        if note is not None or l.startswith("{"):
            note = (note + " " + l) if note is not None else l
            if note.endswith("}"):
                text = note[1:-1].strip()
                note = None
                target = current_article if current_article is not None else current_section if current_section is not None else None
                if target is not None:
                    target["notes"].append(text)
                else:
                    preamble.append({"note": text})
            continue
        if l == "***":
            closing = [x for x in lines[i:] if x]
            break
        m = SECTION.match(l)
        if m:
            title = lines[i]
            i += 1
            current_section = {"num": m.group(1), "title": title, "articles": [], "text": [], "notes": []}
            current_article = None
            sections.append(current_section)
            continue
        m = ARTICLE.match(l)
        if m and current_section is not None:
            current_article = {"num": m.group(1), "parts": [m.group(2)] if m.group(2) else [], "notes": []}
            current_section["articles"].append(current_article)
            continue
        if current_section is None:
            preamble.append({"text": l})
        elif current_article is not None:
            current_article["parts"].append(l)
        else:
            current_section["text"].append(l)
    if note is not None:
        sys.exit("An editorial note is not closed")

    nums = [a["num"] for s in sections for a in s["articles"]]
    if len(nums) != len(set(nums)):
        sys.exit("Article numbers repeat")
    for must in ("1", "17", "64", "65", "157", "161"):
        if must not in nums:
            sys.exit(f"Article {must} is missing")
    if any(not a["parts"] for s in sections for a in s["articles"]):
        sys.exit("An article without text")
    if len(sections) != 14 or sections[-1]["num"] != "XV" or not sections[-1]["text"]:
        sys.exit("Unexpected sections")

    out = {
        "title": "Конституція України",
        "act": "Конституція України від 28.06.1996 № 254к/96-ВР",
        "edition": "Редакція від 01.01.2020, підстава — Закон № 27-IX",
        "source": "https://zakon.rada.gov.ua/laws/show/254к/96-вр",
        "retrieved": "31.10.2026",
        "license": "Офіційний документ; джерело — Верховна Рада України, zakon.rada.gov.ua (CC BY 4.0)",
        "preamble": preamble,
        "sections": sections,
        "closing": closing,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"Constitution: {len(sections)} sections, {len(nums)} articles, {sum(len(s['text']) for s in sections)} other paragraphs")


if __name__ == "__main__":
    main()
