#!/usr/bin/env python3
"""
Turns the Health Icons kept unchanged in healthicons/ (healthicons.org, CC0 1.0: public domain)
into Android vector drawables, one solid colour, tinted by the app.

    python3 scripts/pictograms/healthicons.py  ->  app/src/main/res/drawable/hi_<name>.xml
"""
import os, re, glob

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable")

for f in sorted(glob.glob(os.path.join(HERE, "healthicons", "*.svg"))):
    name = os.path.basename(f)[:-4]
    svg = open(f, encoding="utf-8").read()
    # Only plain paths are expected; anything else would be lost, so it stops here
    if re.search(r"<(circle|rect|ellipse|polygon|g|use|mask|clipPath)\b|transform=", svg):
        raise SystemExit(f"{name}: unexpected element")
    w = re.search(r'viewBox="0 0 (\d+) (\d+)"', svg)
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- Health Icons "{name}" (healthicons.org, CC0 1.0), built by scripts/pictograms/healthicons.py -->',
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="{w.group(1)}" android:viewportHeight="{w.group(2)}">']
    for m in re.finditer(r"<path([^>]*)/?>", svg):
        attrs = m.group(1)
        d = re.search(r' d="([^"]+)"', attrs).group(1)
        rule = ' android:fillType="evenOdd"' if 'fill-rule="evenodd"' in attrs else ""
        out.append(f'    <path android:fillColor="#FF000000"{rule} android:pathData="{d}"/>')
    out.append("</vector>")
    open(os.path.join(OUT, f"hi_{name.replace('-', '_')}.xml"), "w", encoding="utf-8").write("\n".join(out) + "\n")
    print(name)
