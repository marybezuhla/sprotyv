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
                    letter = current["label"].split()[-1] if current["label"] else "X"
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

    guide = {
        "title": "Довідник виживання",
        "disclaimer": disclaimer,
        "version": version,
        "source": "https://github.com/snegireff/dovidnyk-vyzhyvannia",
        "commit": commit,
        "license": "CC BY-SA 4.0",
        "changes": "Перетворено для показу в застосунку: розмітку змінено, рисунки збережено як зображення, "
                   "обкладинку, зміст і бланки нотаток не включено. Текст не змінено.",
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
