#!/usr/bin/env python3
"""
The Rules of the Road of Ukraine (Cabinet of Ministers resolution No. 1306 of 10.10.2001) in full,
as one JSON file for the app: the sections, every point word for word, and the tables.

Input: pdr/pdr-2026-10-14.html, the rules' part of the print page on the Verkhovna Rada's site
(zakon.rada.gov.ua/laws/show/1306-2001-п, "Редакція від 14.10.2026"), kept unchanged. An official
document: not protected by copyright; the site's content is also under CC BY 4.0. The amendment
notes in braces are left out; nothing is reworded. The annexes are pictures of signs, markings and
lights and are not included: sections 33 and 34 name and explain every sign and line in words.

    python3 scripts/content/pdr.py  ->  content/src/main/assets/pdr.json
"""
import json, os, re
from html.parser import HTMLParser

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "pdr", "pdr-2026-10-14.html")
OUT = os.path.join(HERE, "..", "..", "content", "src", "main", "assets", "pdr.json")


def clean(t):
    # Amendment notes, innermost first (some hold braces of their own)
    while True:
        u = re.sub(r"\s*\{[^{}]*\}", "", t)
        if u == t:
            break
        t = u
    t = re.sub(r"[ \t ​\r]+", " ", t)
    t = re.sub(r" *\n *", "\n", t)
    return re.sub(r"\n{2,}", "\n", t).strip()


class Parser(HTMLParser):
    """Paragraphs outside tables, and tables as rows of cells, in page order."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.items, self.buf, self.cls = [], None, ""
        self.tables, self.cell = [], None

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "table":
            self.tables.append([])
        elif tag == "tr" and self.tables:
            self.tables[-1].append([])
        elif tag in ("td", "th") and self.tables:
            self.cell = []
        elif tag == "p" and not self.tables:
            self.buf, self.cls = [], a.get("class", "")
        elif tag == "br":
            (self.cell if self.cell is not None else self.buf if self.buf is not None else []).append("\n")

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.cell is not None and self.tables:
            if self.tables[-1]:
                self.tables[-1][-1].append(clean("".join(self.cell)))
            self.cell = None
        elif tag == "table" and self.tables:
            rows = [r for r in self.tables.pop() if any(r)]
            if self.tables:  # a table inside a cell: its text joins the cell
                if self.tables[-1] and self.tables[-1][-1] is not None:
                    self.cell = (self.cell or []) + [" ".join(" ".join(r) for r in rows)]
            elif rows:
                self.items.append(("table", rows))
        elif tag == "p" and self.buf is not None and not self.tables:
            t = clean("".join(self.buf))
            if t:
                self.items.append((self.cls, t))
            self.buf = None

    def handle_data(self, d):
        if self.cell is not None:
            self.cell.append(d)
        elif self.buf is not None:
            self.buf.append(d)


SECTION = re.compile(r"^(\d{1,2})\. (.+)$")


def main():
    html = open(SRC, encoding="utf-8").read()
    edition = re.search(r"Редакція від (\d\d\.\d\d\.\d{4}), підстава ([^;]+);", html)
    retrieved = re.search(r"retrieved (\d\d\.\d\d\.\d{4})", html).group(1)
    p = Parser()
    p.feed(html)
    sections, cur = [], None
    for cls, x in p.items:
        if cls == "rvps7" and SECTION.match(x):
            num, title = SECTION.match(x).groups()
            cur = {"num": num, "title": title.rstrip("*"), "blocks": []}
            sections.append(cur)
            continue
        if cur is None:
            continue  # the title and the note before section 1
        if cls == "table":
            # Notes set in a two-column frame ("Примітки:" | the notes) and one-cell frames are plain text
            if x[0][0].startswith("_"):
                cur["blocks"].append({"h": x[0][0].strip("_\n :.")})
                for r in x:
                    if r[-1]:
                        cur["blocks"].append({"p": r[-1]})
            elif all(len(r) == 1 for r in x):
                for r in x:
                    cur["blocks"].append({"p": r[0]})
            else:
                cur["blocks"].append({"rows": x})
        elif cls == "rvps12" and len(x) < 80 and not re.match(r"^[\d,.;*() ]+$", x):
            cur["blocks"].append({"h": x})
        else:
            cur["blocks"].append({"p": x})
    data = {
        "title": "Правила дорожнього руху",
        "act": "Постанова Кабінету Міністрів України від 10.10.2001 № 1306",
        "edition": "Редакція від %s, підстава — %s" % edition.groups(),
        "source": "https://zakon.rada.gov.ua/laws/show/1306-2001-п",
        "retrieved": retrieved,
        "license": "Офіційний документ; джерело — Верховна Рада України, zakon.rada.gov.ua (CC BY 4.0)",
        "sections": sections,
    }
    assert [s["num"] for s in sections] == [str(i) for i in range(1, 35)], [s["num"] for s in sections]
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    n = sum(len(s["blocks"]) for s in sections)
    print(data["edition"], len(sections), "sections", n, "blocks", os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
