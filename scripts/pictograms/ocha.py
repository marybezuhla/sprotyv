#!/usr/bin/env python3
"""
Turns the OCHA Humanitarian Icons kept unchanged in ocha/ (OCHA, freely available; listed as
public domain, CC0) into Android vector drawables, one solid colour, tinted by the app.

    python3 scripts/pictograms/ocha.py  ->  app/src/main/res/drawable/ocha_<name>.xml
"""
import os, re, glob

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable")

for f in sorted(glob.glob(os.path.join(HERE, "ocha", "*.svg"))):
    name = os.path.basename(f)[:-4]
    svg = open(f, encoding="utf-8").read()
    # Plain paths in plain groups only; anything else would be lost, so it stops here
    if re.search(r"<(ellipse|polygon|polyline|line|use|mask|clipPath|image)\b|transform=", svg):
        raise SystemExit(f"{name}: unexpected element")
    box = re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg)
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- OCHA Humanitarian Icon "{name}" (OCHA, freely available / CC0), built by scripts/pictograms/ocha.py -->',
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="{box.group(1)}" android:viewportHeight="{box.group(2)}">']
    def num(attrs, k):
        v = re.search(r'\s' + k + r'="([-\d.]+)"', attrs)
        return float(v.group(1)) if v else 0.0
    for m in re.finditer(r"<(path|circle|rect)([^>]*?)/?>", svg, re.S):
        kind, attrs = m.group(1), m.group(2)
        if kind == "circle":
            # A circle as two half arcs, the same shape
            cx, cy, r = num(attrs, "cx"), num(attrs, "cy"), num(attrs, "r")
            d = f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0Z"
        elif kind == "rect":
            x, y, w, h = num(attrs, "x"), num(attrs, "y"), num(attrs, "width"), num(attrs, "height")
            d = f"M{x},{y}h{w}v{h}h{-w}Z"
        else:
            d = re.sub(r"\s+", " ", re.search(r'\sd="([^"]+)"', attrs, re.S).group(1)).strip()
        rule = ' android:fillType="evenOdd"' if 'evenodd' in attrs else ""
        out.append(f'    <path android:fillColor="#FF000000"{rule} android:pathData="{d}"/>')
    out.append("</vector>")
    open(os.path.join(OUT, "ocha_" + re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") + ".xml"), "w", encoding="utf-8").write("\n".join(out) + "\n")
    print(name)
