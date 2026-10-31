#!/usr/bin/env python3
"""
Turns the OpenMoji emoji kept unchanged in openmoji/ (OpenMoji, CC BY-SA 4.0) into Android
vector drawables with their own colours, for the children's cards.

    python3 scripts/pictograms/openmoji.py  ->  app/src/main/res/drawable/om_<name>.xml
"""
import os, re, glob

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable")

def colour(v):
    v = v.strip()
    if re.fullmatch(r"#[0-9a-fA-F]{3}", v):
        v = "#" + "".join(c * 2 for c in v[1:])
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", v):
        raise SystemExit(f"unexpected colour {v}")
    return "#FF" + v[1:].upper()

for f in sorted(glob.glob(os.path.join(HERE, "openmoji", "*.svg"))):
    name = os.path.basename(f)[:-4]
    svg = open(f, encoding="utf-8").read()
    if re.search(r"<(ellipse|use|mask|clipPath|image|linearGradient|radialGradient)\b|transform=|opacity=", svg):
        raise SystemExit(f"{name}: unexpected element")
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- OpenMoji "{name}" (openmoji.org, CC BY-SA 4.0), built by scripts/pictograms/openmoji.py -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="72dp" android:height="72dp" android:viewportWidth="72" android:viewportHeight="72">']
    def attr(a, k):
        m = re.search(r'\s' + k + r'="([^"]*)"', a)
        return m.group(1) if m else None
    def num(a, k):
        return float(attr(a, k) or 0)
    for m in re.finditer(r"<(path|circle|rect|line|polygon|polyline)\b([^>]*?)/?>", svg, re.S):
        kind, a = m.group(1), m.group(2)
        if kind == "circle":
            cx, cy, r = num(a, "cx"), num(a, "cy"), num(a, "r")
            d = f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0Z"
        elif kind == "rect":
            x, y, w, h = num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height")
            d = f"M{x},{y}h{w}v{h}h{-w}Z"
        elif kind == "line":
            d = f"M{num(a, 'x1')},{num(a, 'y1')}L{num(a, 'x2')},{num(a, 'y2')}"
        elif kind in ("polygon", "polyline"):
            pts = re.split(r"[\s,]+", attr(a, "points").strip())
            pairs = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts), 2)]
            d = "M" + "L".join(pairs) + ("Z" if kind == "polygon" else "")
        else:
            d = re.sub(r"\s+", " ", attr(a, "d")).strip()
        fill, stroke = attr(a, "fill"), attr(a, "stroke")
        props = []
        if fill is None and kind != "line":
            fill = "#000000"
        if fill and fill != "none":
            props.append(f'android:fillColor="{colour(fill)}"')
        if stroke and stroke != "none":
            props += [f'android:strokeColor="{colour(stroke)}"', f'android:strokeWidth="{attr(a, "stroke-width") or 1}"']
            if attr(a, "stroke-linecap"): props.append(f'android:strokeLineCap="{attr(a, "stroke-linecap")}"')
            if attr(a, "stroke-linejoin"): props.append(f'android:strokeLineJoin="{attr(a, "stroke-linejoin")}"')
            if attr(a, "stroke-miterlimit"): props.append(f'android:strokeMiterLimit="{attr(a, "stroke-miterlimit")}"')
        if not props:
            continue
        out.append(f'    <path {" ".join(props)} android:pathData="{d}"/>')
    out.append("</vector>")
    open(os.path.join(OUT, f"om_{name}.xml"), "w", encoding="utf-8").write("\n".join(out) + "\n")
    print("om_" + name)
