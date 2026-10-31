#!/usr/bin/env python3
"""
The 29 procedures of first aid ("домедична допомога") approved by order No. 441 of the Ministry
of Health of Ukraine, 09.03.2022 (registered with the Ministry of Justice 28.03.2022, No. 356/37692),
as one JSON file for the app. An official document: not protected by copyright (Law No. 2811-IX,
art. 8 part 1 item 3), so its text is kept word for word; only the layout is read from it.

Input: moz441/NN.txt, the text of each annex PDF from moz.gov.ua (pdftotext -layout), kept unchanged.

    python3 scripts/content/moz441.py  ->  content/src/main/assets/moz441.json
"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "content", "src", "main", "assets", "moz441.json")

POINT = re.compile(r"^(\d{1,2})\.\s+(.*)$")
SUB = re.compile(r"^(\d{1,2})\)\s+(.*)$")
SUBSUB = re.compile(r"^([абвгґдеєжзиіїйклмнопрстуфхцчшщюя])\)\s+(.*)$")

def norm(s):
    return re.sub(r"\s+", " ", s.replace("’", "'").replace("`", "'")).strip()

def parse(path):
    lines = open(path, encoding="utf-8").read().replace("\f", "\n").split("\n")
    # From "ПОРЯДОК" to the signature
    start = next(i for i, l in enumerate(lines) if l.strip() == "ПОРЯДОК")
    body = []
    for l in lines[start + 1:]:
        if l.strip().startswith("Генеральний директор"):
            break
        if re.fullmatch(r"\s*\d{1,2}\s*", l):  # page numbers
            continue
        body.append(l.strip())
    title, i = [], 0
    while not POINT.match(body[i]):
        if body[i]:
            title.append(body[i])
        i += 1
    title = norm(" ".join(title))
    points, cur = [], None  # cur: the item text is appended to
    for l in body[i:]:
        if not l:
            continue
        m = POINT.match(l)
        if m:
            points.append({"n": m.group(1), "text": m.group(2), "items": []})
            cur = points[-1]
            continue
        m = SUB.match(l)
        if m and points:
            points[-1]["items"].append({"n": m.group(1), "text": m.group(2), "items": []})
            cur = points[-1]["items"][-1]
            continue
        m = SUBSUB.match(l)
        if m and points and points[-1]["items"]:
            points[-1]["items"][-1]["items"].append({"n": m.group(1), "text": m.group(2)})
            cur = points[-1]["items"][-1]["items"][-1]
            continue
        # A broken line: "серцево-" + "легеневу" keeps its hyphen, other lines join with a space
        cur["text"] += ("" if cur["text"].endswith("-") else " ") + l
    def clean(x):
        x["text"] = norm(x["text"])
        for y in x.get("items", []):
            clean(y)
    for p in points:
        clean(p)
    return title, points

def main():
    procedures = []
    for name in sorted(os.listdir(os.path.join(HERE, "moz441"))):
        if not name.endswith(".txt"):
            continue
        n = int(name[:-4])
        title, points = parse(os.path.join(HERE, "moz441", name))
        procedures.append({"n": n, "title": title, "points": points})
    assert [p["n"] for p in procedures] == list(range(1, 30)), "all 29 procedures"
    doc = {
        "title": "Порядки надання домедичної допомоги особам при невідкладних станах",
        "act": "Наказ МОЗ України від 09.03.2022 № 441, зареєстровано в Мін'юсті 28.03.2022 за № 356/37692",
        "source": "https://moz.gov.ua/uk/decrees/nakaz-moz-ukraini-vid-09032022--441-pro-zatverdzhennja-porjadkiv-nadannja-domedichnoi-dopomogi-osobam-pri-nevidkladnih-stanah",
        "retrieved": "2026-10-31",
        "license": "Офіційний документ: не охороняється авторським правом (Закон № 2811-IX, ст. 8)",
        "procedures": procedures,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print(len(procedures), "procedures,", sum(len(p["points"]) for p in procedures), "points")

if __name__ == "__main__":
    main()
