#!/usr/bin/env python3
"""
Turns the open "Довідник виживання" (CC BY-SA 4.0, github.com/snegireff/dovidnyk-vyzhyvannia)
into the offline guide inside Sprotyv: content/src/main/assets/guide.json and the figures as SVG
files next to it (scripts/render_figures.js turns them into pictures).

Standard library only, so nothing extra is installed to run it. The source is read as data:
only the expected tags are understood, everything else stops the script.

    python3 scripts/build_guide.py <path to the guide repository> [--out content/src/main/assets]

The text is kept as written. Only the markup is changed: bold becomes **…**, line breaks become
"\n", figures become references to picture files.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from html.parser import HTMLParser

# Chapters left out, and why
SKIP = {
    "00_cover_toc.html",   # cover and table of contents: the app builds its own
    "99_notes.html",       # blank tables to fill in by hand: comes later as part of "Mine"
    "99z_backcover.html",  # back cover: "first 60 seconds" is card 30.1, numbers are in the Call bar
}

VOID = {"br", "path", "line", "rect", "circle", "ellipse", "polygon", "polyline", "stop"}
BLOCK_CALLOUTS = {"warning", "tip", "critical", "note"}
MAX_TEXT = 20000


# Changes for current evidence; GUIDE-REVIEW.md gives the source and date of each
EVIDENCE = [
    # 7.2: a percentage without a source
    ("У 80% випадків сильний безперервний тиск 10+ хвилин зупиняє кровотечу.",
     "Сильний безперервний тиск 10+ хвилин зупиняє більшість кровотеч."),
    # 7.5 and 7.1: a vented seal first (RCUK 2025); otherwise occlusive, as MOZ order 441 says
    ("**Проникна рана грудей** (свист повітря, піниста кров): негайно закрити долонею, потім герметичною наліпкою (оклюзійною) або поліетиленом/пакетом, приклеєним скотчем.",
     "**Проникна рана грудей** (свист повітря, піниста кров): найкраще — наліпка з клапаном (вентильована), вона випускає повітря назовні. Немає її — оклюзійна наліпка або поліетилен/пакет, приклеєний скотчем з усіх боків (накладати на видиху), як велить порядок МОЗ."),
    ("Оклюзійні наліпки грудні — 2 шт. (або щільна плівка + скотч)",
     "Оклюзійні наліпки грудні, краще з клапаном — 2 шт. (або щільна плівка + скотч)"),
    ("Проникна рана грудей — оклюзійна наліпка (п. 7.5)", "Проникна рана грудей — наліпка, краще з клапаном (п. 7.5)"),
    # 7.8 and 7.16: ibuprofen is avoided in pregnancy (NHS)
    ("Знеболення (парацетамол/ібупрофен), багато пити.",
     "Знеболення (парацетамол або ібупрофен; вагітним — лише парацетамол), багато пити."),
    ("Парацетамол 500 мг, ібупрофен 200–400 мг (знеболення, жар)",
     "Парацетамол 500 мг, ібупрофен 200–400 мг (знеболення, жар; вагітним — лише парацетамол)"),
    # 7.10: cold water immersion first (MOZ order 441, RCUK 2025: 15 minutes or until confusion passes, whichever comes first)
    ("У тінь, роздягнути, охолоджувати: мокрі тканини на шию/пахви/пах, обмахувати, прохолодне пиття малими ковтками.",
     "У тінь, 103, роздягнути й охолоджувати негайно. Найкраще — занурити тіло до шиї в прохолодну чи холодну воду (ванна, бочка, водойма біля берега; голова над водою) і тримати там до 15 хвилин або доки не мине сплутаність, що настане раніше; людину у воді самого не лишати. Немає такої змоги — холодний душ, мокрі простирадла, холодні пакети на шию/пахви/пах і обмахування. Притомному — прохолодне пиття малими ковтками."),
    # 7.4 and 7.6: a pregnant woman lies on her left side (St John Ambulance Cymru)
    ("Перевіряйте дихання щохвилини. Кожні 30 хв — перевернути на інший бік (якщо чекання довге).",
     "Вагітну з помітним животом кладіть на лівий бік. Перевіряйте дихання щохвилини. Кожні 30 хв — перевернути на інший бік (якщо чекання довге; вагітну — лишати на лівому)."),
    ("Покласти горизонтально; якщо немає травм голови/грудей/живота — трохи підняти ноги (на рюкзак).",
     "Покласти горизонтально; якщо немає травм голови/грудей/живота — трохи підняти ноги (на рюкзак). Вагітну з помітним животом — на лівий бік: на спині матка тисне на великі судини."),
    # 7.11: how much sugar (RCUK 2025: 15–20 g; MOZ order 441: half a glass of juice)
    ("Притомному — солодке (цукор, сік, мед) негайно, потім хліб/печиво;",
     "Притомному — 15–20 г цукру негайно (4–5 чайних ложок цукру чи таблетки глюкози, або півсклянки соку чи солодкого, не дієтичного напою), потім хліб/печиво;"),
    # 21.2: ibuprofen is avoided in pregnancy (NHS)
    ("або ібупрофен 200–400 мг до 3 р/день (після їжі). Дітям — тільки дитячі дози за вагою, НЕ аспірин.",
     "або ібупрофен 200–400 мг до 3 р/день (після їжі; вагітним — лише парацетамол). Дітям — тільки дитячі дози за вагою, НЕ аспірин."),
    # 22.5: a share without a source; newborn breaths before compressions (ERC 2025 newborn life support)
    ("у 90% випадків завдання помічника — не заважати,", "здебільшого завдання помічника — не заважати,"),
    ("не дихає за хвилину — 5 вдихів «рот у рот і ніс» дуже м'яко, потім 3 натискання двома великими пальцями, обхопивши грудну клітку долонями, на 1 вдих, 103.",
     "не дихає за хвилину — 5 повільних вдихів «рот у рот і ніс» дуже м'яко, далі вдихи приблизно раз на 2 секунди, 103; не починає дихати за 30 секунд таких вдихів — 3 натискання двома великими пальцями, обхопивши грудну клітку долонями, на 1 вдих."),
    # 27.5: a second dose of adrenaline after five minutes (RCUK/ERC 2025)
    ("це анафілаксія: адреналін (автоін'єктор), якщо є, антигістамінний,",
     "це анафілаксія: адреналін (автоін'єктор у зовнішній бік стегна), якщо є; за 5 хв не краще — друга доза в інше стегно, якщо є; антигістамінний,"),
    # 9.4: nothing unclean on a wound (NHS, cuts and grazes: rinse under running water)
    ("Молоде листя в суп; лист — на дрібні рани",
     "Молоде листя в суп. На рану лист не прикладайте: її промивають чистою водою"),
    # 12.1: it is water that carries heat away about 25 times faster than air, not the shirt
    ("мокра бавовняна футболка відводить тепло у 25 разів швидше за повітря і не сохне.",
     "вода відводить тепло приблизно в 25 разів швидше за повітря, а мокра бавовна довго не сохне."),
    # 12.2: a figure without a source
    ("усередині на 5–10° тепліше від самого дихання.", "усередині помітно тепліше завдяки теплу тіл і диханню."),
    # 12.3: the real reason to go before sleep
    ("Повний сечовий міхур забирає тепло — сходіть перед сном, хоч би як хотілося лишатися в теплі.",
     "Сходіть у туалет перед сном, хоч би як хотілося лишатися в теплі: тоді не доведеться вилазити на холод серед ночі."),
    # 13.2: moss grows where it is damp and shaded, on any side
    ("Мох і лишайник густіші з північного (вологого) боку каменів і дерев; кора темніша і грубіша з півночі.",
     "Мох і лишайник ростуть там, де тінь і волога: нерідко з півночі, але ця ознака ненадійна, особливо в лісі й місті."),
    # 16.2: urine is not sterile, but faeces carry most of the danger (WHO guidelines on excreta, vol. 4, 2006)
    ("вона майже стерильна, її легко вилити подалі", "у ній значно менше небезпечних мікробів, ніж у калі, її легко вилити подалі"),
    # 20.1: how long food stays cold (USDA FSIS)
    ("закрита морозилка тримає холод 24–48 год.",
     "закрита морозилка тримає холод 24–48 год (повна — близько 2 діб, наполовину порожня — близько доби), холодильник — лише близько 4 год."),
    # 20.5: a forecast for one winter without a source
    ("розвідка і енергетики попереджають, що взимку 2026–2027 удари по тепловій генерації посиляться (реактивні дрони, балістика), а великі міста можуть лишатися без тепла тижнями.",
     "удари по енергетиці й тепловій генерації (дрони, балістика) повторюються щозими, тож готуйтеся до того, що опалення може не бути довго."),
    # 29.4: colour is no sign of done meat (USDA FSIS: poultry 74 °C, minced meat 71 °C)
    ("Сік прозорий, всередині 70°C+, курка — 75°C+", "Усередині 71°C+, птиця — 75°C+; колір соку й м'яса — ненадійна ознака"),
]


def replace_text(chapters, pairs):
    """Exact replacements in the text, everywhere; each must be found at least once, or the script stops."""
    blob = json.dumps(chapters, ensure_ascii=False)
    for old, new in pairs:
        o, n = json.dumps(old, ensure_ascii=False)[1:-1], json.dumps(new, ensure_ascii=False)[1:-1]
        if blob.count(o) < 1:
            sys.exit(f"Not found in the guide: {old}")
        blob = blob.replace(o, n)
    chapters[:] = json.loads(blob)


def child_doses_out(chapters, children_row):
    """
    Sprotyv's own rule: doses for children only from an official source after a paediatrician's
    review. Until then the children's rows of the potassium iodide table (6.2) become one row that
    points to the official announcement or a doctor. Fails loudly if the table changes upstream.
    """
    for c in chapters:
        for s in c["sections"]:
            if s["num"] != "6.2":
                continue
            for b in s["blocks"]:
                for x in b.get("blocks", []) + [b]:
                    if x.get("t") != "table":
                        continue
                    rows = x["rows"]
                    kids = [i for i, r in enumerate(rows) if not r["head"] and r["cells"][0].lower().startswith(("діти", "немовлят", "children", "infants"))]
                    if kids:
                        x["rows"] = rows[:kids[0]] + [{"head": False, "cells": children_row}] + [r for i, r in enumerate(rows) if i > kids[-1]]
                        return
    sys.exit("The potassium iodide table in 6.2 was not found")


class Node:
    def __init__(self, tag, attrs, parent=None):
        self.tag = tag
        self.attrs = dict(attrs)
        self.children = []
        self.parent = parent

    def cls(self):
        return self.attrs.get("class", "")


class Tree(HTMLParser):
    """A plain tree of the chapter; raw SVG markup is kept aside for figures."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node("root", [])
        self.cur = self.root
        self.svg_depth = 0
        self.svg_parts = []

    def handle_starttag(self, tag, attrs):
        if tag == "svg" or self.svg_depth:
            self.svg_parts.append(self.get_starttag_text())
            if tag in VOID:
                self.svg_parts.append("")
                return
            self.svg_depth += 1
            if tag == "svg":
                node = Node("svg", attrs, self.cur)
                self.cur.children.append(node)
                self.cur = node
            return
        node = Node(tag, attrs, self.cur)
        self.cur.children.append(node)
        if tag not in VOID:
            self.cur = node

    def handle_startendtag(self, tag, attrs):
        if self.svg_depth:
            self.svg_parts.append(self.get_starttag_text())
            return
        self.cur.children.append(Node(tag, attrs, self.cur))

    def handle_endtag(self, tag):
        if self.svg_depth:
            if tag in VOID:
                return
            self.svg_parts.append(f"</{tag}>")
            self.svg_depth -= 1
            if tag == "svg":
                self.cur.svg = "".join(self.svg_parts)
                self.svg_parts = []
                self.cur = self.cur.parent
            return
        if tag in VOID:
            return
        # Close up to the matching tag (the guide's HTML is well formed; this only guards)
        n = self.cur
        while n is not self.root and n.tag != tag:
            n = n.parent
        if n is self.root:
            raise ValueError(f"Unexpected </{tag}>")
        self.cur = n.parent

    def handle_data(self, data):
        if self.svg_depth:
            self.svg_parts.append(data.replace("&", "&amp;").replace("<", "&lt;"))
            return
        self.cur.children.append(data)

    def handle_comment(self, data):
        if self.svg_depth:
            return


def inline(node):
    """Text of an inline run: bold as **…**, line breaks as \\n, spaces tidied."""
    out = []

    def walk(n):
        for c in n.children:
            if isinstance(c, str):
                out.append(c)
            elif c.tag == "b":
                raw = inline_raw(c)
                inner = raw.strip()
                if inner:
                    lead = raw[: len(raw) - len(raw.lstrip())]
                    trail = raw[len(raw.rstrip()):]
                    out.append(lead + "**" + inner + "**" + trail)
                else:
                    out.append(raw)
            elif c.tag == "br":
                out.append("\n")
            elif c.tag in ("span", "i", "em", "small"):
                walk(c)
            else:
                raise ValueError(f"Unexpected <{c.tag}> inside text")

    walk(node)
    text = tidy("".join(out)).strip()
    if "***" in text:
        raise ValueError("Nested bold")
    if len(text) > MAX_TEXT:
        raise ValueError("Text too long")
    return text


def tidy(text):
    text = re.sub(r"[ \t\r\f\v]*\n[ \t\r\f\v]*", "\n", text)
    return re.sub(r"[ \t\r\f\v]+", " ", text)


def inline_raw(node):
    """Inline text with its outer spaces kept (for bold runs)."""
    holder = Node("span", [])
    holder.children = list(node.children)
    text = inline(holder)
    kids = node.children
    lead = " " if kids and isinstance(kids[0], str) and kids[0][:1].isspace() else ""
    trail = " " if kids and isinstance(kids[-1], str) and kids[-1][-1:].isspace() else ""
    return lead + text + trail


def has_blocks(node):
    return any(not isinstance(c, str) and c.tag in ("ul", "ol", "p", "div", "table") for c in node.children)


def list_items(node):
    items = []
    for li in node.children:
        if isinstance(li, str):
            if li.strip():
                raise ValueError("Text between list items")
            continue
        if li.tag != "li":
            raise ValueError(f"Unexpected <{li.tag}> in a list")
        if has_blocks(li):
            # A list item with a nested list: its own text first, then the nested list.
            # Anything else inside an item would be lost, so it stops the build.
            for c in li.children:
                if not isinstance(c, str) and c.tag not in ("b", "br", "span", "i", "em", "small", "ul", "ol"):
                    raise ValueError(f"Unexpected <{c.tag}> inside a list item")
            head = Node("li", [])
            head.children = [c for c in li.children if isinstance(c, str) or c.tag not in ("ul", "ol")]
            sub = [c for c in li.children if not isinstance(c, str) and c.tag in ("ul", "ol")]
            items.append({"text": inline(head), "sub": [s for l in sub for s in list_items(l)]})
        else:
            items.append({"text": inline(li)})
    return items


def table(node):
    rows = []
    for tr in [c for c in node.children if not isinstance(c, str)]:
        if tr.tag in ("thead", "tbody"):
            rows.extend(table(tr)["rows"])
            continue
        if tr.tag != "tr":
            raise ValueError(f"Unexpected <{tr.tag}> in a table")
        cells = [c for c in tr.children if not isinstance(c, str)]
        for c in cells:
            if c.tag not in ("td", "th"):
                raise ValueError(f"Unexpected <{c.tag}> in a table row")
            if "colspan" in c.attrs or "rowspan" in c.attrs:
                raise ValueError("Merged table cells are not supported")
        rows.append({"head": all(c.tag == "th" for c in cells), "cells": [inline(c) for c in cells]})
    widths = {len(r["cells"]) for r in rows}
    if len(widths) > 1:
        raise ValueError("Table rows of different widths")
    return {"rows": rows}


class Builder:
    def __init__(self, svg_dir):
        self.svg_dir = svg_dir
        self.figures = {}

    def figure(self, node):
        svgs = []
        caption = ""

        def walk(n):
            nonlocal caption
            for c in n.children:
                if isinstance(c, str):
                    if c.strip():
                        raise ValueError("Loose text inside a figure")
                    continue
                if c.tag == "svg":
                    svgs.append(c.svg)
                elif c.tag == "figcaption":
                    if caption:
                        raise ValueError("A figure with two captions")
                    caption = inline(c)
                elif c.tag == "div":
                    walk(c)
                else:
                    raise ValueError(f"Unexpected <{c.tag}> inside a figure")

        walk(node)
        names = []
        for svg in svgs:
            digest = hashlib.sha256(svg.encode("utf-8")).hexdigest()[:16]
            name = f"fig_{digest}"
            self.figures[name] = svg
            names.append(name)
        if not names:
            raise ValueError("A figure without a picture")
        return {"t": "figure", "pictures": names, "caption": caption}

    def blocks(self, node):
        out = []
        for c in node.children:
            if isinstance(c, str):
                if c.strip():
                    raise ValueError(f"Loose text: {c.strip()[:60]}")
                continue
            if c.tag == "p":
                if c.cls() not in ("", "chapter-intro"):
                    raise ValueError(f"Unknown paragraph class '{c.cls()}'")
                text = inline(c)
                if text:
                    out.append({"t": "intro" if "chapter-intro" in c.cls() else "p", "text": text})
            elif c.tag == "h3":
                out.append({"t": "h", "text": inline(c)})
            elif c.tag in ("ul", "ol"):
                if c.cls() not in ("", "steps", "checklist"):
                    raise ValueError(f"Unknown list class '{c.cls()}'")
                kind = "steps" if c.tag == "ol" else ("check" if "checklist" in c.cls() else "bullets")
                out.append({"t": "list", "kind": kind, "items": list_items(c)})
            elif c.tag == "table":
                out.append({"t": "table", **table(c)})
            elif c.tag == "figure":
                out.append(self.figure(c))
            elif c.tag == "div" and c.cls() in BLOCK_CALLOUTS:
                if has_blocks(c):
                    out.append({"t": "callout", "kind": c.cls(), "blocks": self.blocks(c)})
                else:
                    out.append({"t": "callout", "kind": c.cls(), "blocks": [{"t": "p", "text": inline(c)}]})
            elif c.tag == "div" and c.cls() == "figrow":
                out.extend(self.blocks(c))
            elif c.tag == "div" and not c.cls():
                out.extend(self.blocks(c))
            else:
                raise ValueError(f"Unexpected <{c.tag} class='{c.cls()}'>")
        return out

    def chapter_file(self, path):
        tree = Tree()
        with open(path, encoding="utf-8") as f:
            tree.feed(f.read())
        tree.close()
        chapters = []
        current = None
        section = None
        loose = Node("root", [])

        def flush():
            nonlocal loose
            if section is not None:
                section["blocks"].extend(self.blocks(loose))
            elif current is not None:
                current["intro"].extend(self.blocks(loose))
            elif any(not isinstance(c, str) or c.strip() for c in loose.children):
                raise ValueError("Content before the first chapter heading")
            loose = Node("root", [])

        for c in tree.root.children:
            if not isinstance(c, str) and c.tag == "h1":
                flush()
                num = ""
                title_node = Node("h1", [])
                for x in c.children:
                    if not isinstance(x, str) and x.tag == "span" and "chnum" in x.cls():
                        num = inline(x)
                    else:
                        title_node.children.append(x)
                current = {"id": c.attrs.get("id", ""), "label": num, "title": inline(title_node), "intro": [], "sections": []}
                section = None
                chapters.append(current)
            elif not isinstance(c, str) and c.tag == "h2":
                flush()
                if current is None:
                    raise ValueError("A section before any chapter")
                title = inline(c)
                m = re.match(r"^([0-9А-ЯҐЄІЇ]+(?:\.[0-9]+)*)\.\s+(.*)$", title)
                if m:
                    num, name = m.group(1), m.group(2)
                else:
                    # e.g. "Додаток А" weeks: numbered А.1, А.2 … so every section has its own address
                    if not current["label"].startswith("ДОДАТОК"):
                        raise ValueError(f"A section without a number outside an appendix: {title}")
                    letter = current["label"].split()[-1]
                    num, name = f"{letter}.{len(current['sections']) + 1}", title
                section = {"num": num, "title": name, "blocks": []}
                current["sections"].append(section)
            else:
                loose.children.append(c)
        flush()
        return chapters


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("source")
    ap.add_argument("--out", default="content/src/main/assets")
    args = ap.parse_args()

    chapters_dir = os.path.join(args.source, "chapters")
    files = sorted(f for f in os.listdir(chapters_dir) if f.endswith(".html") and f not in SKIP)
    builder = Builder(None)
    chapters = []
    for f in files:
        try:
            chapters.extend(builder.chapter_file(os.path.join(chapters_dir, f)))
        except ValueError as e:
            sys.exit(f"{f}: {e}")

    for i, ch in enumerate(chapters):
        if not ch["id"]:
            ch["id"] = f"ch-x{i}"
        if not ch["title"]:
            sys.exit(f"Chapter without a title: {ch['id']}")

    # Pictures the guide lacks, from open sources (scripts/figures-extra), each after its heading
    extra_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figures-extra")
    with open(os.path.join(extra_dir, "extras.json"), encoding="utf-8") as f:
        extras = json.load(f)
    for x in extras:
        if not re.fullmatch(r"fig_[0-9a-f]{16}", x["name"]):
            sys.exit(f"Bad extra figure name: {x['name']}")
        placed = 0
        for ch in chapters:
            if ch["title"] != x["chapter"]:
                continue
            for sec in ch["sections"]:
                for i, b in enumerate(sec["blocks"]):
                    if b.get("t") == "h" and b["text"].startswith(x["after"]):
                        sec["blocks"].insert(i + 1, {"t": "figure", "pictures": [x["name"]], "caption": x["caption"]})
                        placed += 1
                        break
        if placed != 1:
            sys.exit(f"Extra figure {x['name']}: heading «{x['after']}» found {placed} times")

    try:
        commit = subprocess.run(["git", "-C", args.source, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        commit = ""
    version = ""
    with open(os.path.join(chapters_dir, "00_cover_toc.html"), encoding="utf-8") as f:
        m = re.search(r"Версія\s+([0-9.]+)", f.read())
        version = m.group(1) if m else ""

    cover = Tree()
    with open(os.path.join(chapters_dir, "00_cover_toc.html"), encoding="utf-8") as f:
        cover.feed(f.read())
    disclaimer = []

    def find_disclaimer(n):
        for c in n.children:
            if isinstance(c, str):
                continue
            if c.tag == "div" and "disclaimer" in c.cls():
                disclaimer.extend(p.strip() for p in inline(c).split("\n\n") if p.strip())
            else:
                find_disclaimer(c)

    find_disclaimer(cover.root)
    if not disclaimer:
        sys.exit("The cover disclaimer was not found")

    # Sprotyv's own changes, each named in "changes" below
    child_doses_out(chapters, ["Діти до 13 років", "Дозу для дітей називає офіційне оголошення МОЗ або лікар"])
    replace_text(chapters, [
        ("та назви офіційних Telegram-каналів ОВА/ДСНС", "та адреси офіційних сайтів і застосунків ОВА/ДСНС"),
        ("(101, 102, чат-боти ОВА/ЗСУ вашої області, запишіть їх на папір заздалегідь)",
         "(101, 102 або інші офіційні способи, які називає ОВА вашої області; запишіть їх на папір заздалегідь)"),
        ("(Organic Maps, maps.me)", "(CoMaps, Organic Maps або OsmAnd)"),  # 13.1 and 18.1
        ("дитині від 3 років — близько 1 г/кг (дітям до 3 років — лише за вказівкою медиків)", "дітям — лише за вказівкою 103 або лікаря"),  # 7.13
    ])
    # Brought in line with current evidence (November 2026): each change, its source and date are in
    # scripts/content/adaptation/GUIDE-REVIEW.md
    replace_text(chapters, EVIDENCE)

    guide = {
        "title": "Довідник виживання",
        "disclaimer": disclaimer,
        "version": version,
        "source": "https://github.com/snegireff/dovidnyk-vyzhyvannia",
        "commit": commit,
        "license": "CC BY-SA 4.0",
        "changes": "Перетворено для показу в застосунку: розмітку змінено, рисунки збережено як зображення, "
                   "розділам Додатку А додано номери А.1–А.5, застереження з обкладинки перенесено в «Про довідник», "
                   "решту обкладинки, зміст і бланки нотаток не включено. Додано три рисунки з відкритих джерел "
                   "(суспільне надбання; автор — у підписі): рис. 11.2, 15.5, 15.6. Текст розділів не змінено, крім такого: "
                   "у п. 4.8 і 14.4 замість чат-ботів і Telegram-каналів — офіційні сайти, застосунки й способи, які називає ОВА "
                   "(застосунок не радить Telegram); у п. 13.1 і 18.1 замість maps.me — CoMaps, Organic Maps або OsmAnd; у таблиці п. 6.2 "
                   "дози для дітей замінено відсилкою до офіційного оголошення МОЗ або лікаря, а в п. 7.13 дозу активованого вугілля "
                   "для дітей — відсилкою до 103 або лікаря (дози для дітей у застосунку — лише після рецензії педіатра). "
                   "У листопаді 2026 року поради звірено з чинними настановами (Resuscitation Council UK і ERC 2025, наказ МОЗ № 441, "
                   "NHS, USDA, ВООЗ) і виправлено в п. 7.1, 7.2, 7.4–7.6, 7.8, 7.10, 7.11, 7.16, 9.4, 12.1–12.3, 13.2, 16.2, 20.1, 20.5, "
                   "21.2, 22.5, 27.5 і 29.4; кожна зміна з джерелом — у файлі scripts/content/adaptation/GUIDE-REVIEW.md сховища застосунку.",
        "chapters": chapters,
    }
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "guide.json"), "w", encoding="utf-8") as f:
        json.dump(guide, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")

    svg_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figures-svg")
    os.makedirs(svg_dir, exist_ok=True)
    for old in os.listdir(svg_dir):
        if old.endswith(".svg") and old[:-4] not in builder.figures:
            os.remove(os.path.join(svg_dir, old))
    for name, svg in builder.figures.items():
        with open(os.path.join(svg_dir, name + ".svg"), "w", encoding="utf-8") as f:
            f.write(svg)

    nums = [s["num"] for c in chapters for s in c["sections"]]
    dup = {n for n in nums if nums.count(n) > 1}
    if dup or "" in nums:
        sys.exit(f"Section numbers must be unique and present: {sorted(dup)}")
    sections = len(nums)
    print(f"Guide {version}: {len(chapters)} chapters, {sections} sections, {len(builder.figures)} figures")


if __name__ == "__main__":
    main()
