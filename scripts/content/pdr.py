#!/usr/bin/env python3
"""
The Rules of the Road (Cabinet of Ministers resolution No. 1306 of 10.10.2001) from the print page
of zakon.rada.gov.ua, as plain paragraphs grouped into sections, word for word. Notes in braces
(amendment history) are left out; the text itself is unchanged. Pictures of signs and markings are
not included: the text names each of them.

    python3 pdr.py pdr.html pdr.json
"""
import json, re, sys
from html.parser import HTMLParser

src, out = sys.argv[1:3]

class P(HTMLParser):
    def __init__(self):
        super().__init__(); self.paras = []; self.cur = None; self.cls = ""; self.depth = 0
    def handle_starttag(self, tag, attrs):
        if tag == "p" or (tag == "div" and dict(attrs).get("class", "").startswith("rvps")):
            self.cur = []; self.cls = dict(attrs).get("class", ""); self.depth = 1
        elif self.cur is not None and tag == "br":
            self.cur.append("\n")
    def handle_endtag(self, tag):
        if self.cur is not None and tag in ("p", "div"):
            t = re.sub(r"[ \t ]+", " ", "".join(self.cur)).strip()
            t = re.sub(r" *\n *", "\n", t)
            if t: self.paras.append((self.cls, t))
            self.cur = None
    def handle_data(self, d):
        if self.cur is not None: self.cur.append(d)

p = P(); p.feed(open(src, encoding="utf-8", errors="replace").read())
paras = p.paras
m = re.search(r"Редакція від (\d\d\.\d\d\.\d{4})", open(src, encoding="utf-8", errors="replace").read())
edition = m.group(1) if m else ""
# The rules start at their title; the resolution's own text before it is kept as an intro
start = next(i for i, (_, t) in enumerate(paras) if t == "ПРАВИЛА ДОРОЖНЬОГО РУХУ")
sections, cur = [], None
head = re.compile(r"^(\d{1,2})\. ([А-ЯҐЄІЇ][А-ЯҐЄІЇ ,'’\-–—()«»\"“”]+)$")
for cls, t in paras[start + 1:]:
    if t.startswith("{") and t.endswith("}"):
        continue  # amendment notes
    t = re.sub(r"\s*\{[^{}]*\}", "", t).strip()
    if not t:
        continue
    h = head.match(t)
    if h or (cls in ("rvps7", "rvps6") and not cur) or (cls == "rvps7" and t.upper() == t and len(t) < 120):
        cur = {"title": t, "paras": []}
        sections.append(cur)
        continue
    if cur is None:
        cur = {"title": "", "paras": []}; sections.append(cur)
    cur["paras"].append(t)
data = {
    "title": "Правила дорожнього руху",
    "act": "Постанова Кабінету Міністрів України № 1306 від 10.10.2001",
    "edition": edition,
    "source": "https://zakon.rada.gov.ua/laws/show/1306-2001-%D0%BF",
    "sections": [s for s in sections if s["paras"] or s["title"]],
}
assert len(data["sections"]) >= 30, len(data["sections"])
json.dump(data, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(edition, len(data["sections"]), "sections", sum(len(s["paras"]) for s in data["sections"]), "paragraphs")
