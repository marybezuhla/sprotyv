#!/usr/bin/env python3
"""
Builds the step pictograms from Material Symbols Rounded, filled (Google, Apache 2.0; the glyphs
are copied unchanged in material/). One solid colour, so the app tints them for any theme.
Parts never overlap: Android cannot cut one shape out of another, so a "no" badge sits in a free
corner. Each pictogram is always shown with its step's words.

    python3 scripts/pictograms/build.py
      -> scripts/pictograms/<name>.svg (preview) and app/src/main/res/drawable/pict_<name>.xml
"""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable")
C = 960  # canvas, the glyphs' own grid

# Each glyph's drawn bounds (x, y, w, h) on its 960 grid, measured once in a browser
BOUNDS = {
    "block": (80, 80, 800, 800), "call": (120, 120, 720, 720), "cloud": (40, 161, 880, 639),
    "destruction": (105, 83, 735, 797), "directions_run": (125, 60, 635, 860), "directions_walk": (232, 60, 521, 860),
    "do_not_touch": (38, 40, 826, 880), "door_front": (120, 120, 720, 720), "door_open": (120, 130, 720, 710), "house": (52, 135, 856, 665),
    "man": (340, 80, 280, 800), "pan_tool": (64, 40, 777, 880), "steps": (84, 90, 796, 790),
    "valve": (160, 120, 640, 711), "window": (120, 120, 720, 720),
}

def glyph_path(name):
    svg = open(os.path.join(HERE, "material", name + ".svg"), encoding="utf-8").read()
    return re.search(r' d="([^"]+)"', svg).group(1)

def g(name, box, rot=0, flip=False):
    """A glyph fitted into box (x, y, w, h), centred, keeping its shape; turned by rot (0, 90, 180, 270)."""
    bx, by, bw, bh = BOUNDS[name]
    nw, nh = (bh, bw) if rot in (90, 270) else (bw, bh)
    x, y, w, h = box
    s = min(w / nw, h / nh)
    ox, oy = x + (w - nw * s) / 2, y + (h - nh * s) / 2
    return ("glyph", glyph_path(name), dict(ox=ox, oy=oy, s=s, bx=bx, by=by, bw=bw, bh=bh, nw=nw, nh=nh, rot=rot, flip=flip))

def rect(x, y, w, h, r):
    return ("path", f"M{x+r} {y}H{x+w-r}A{r} {r} 0 0 1 {x+w} {y+r}V{y+h-r}A{r} {r} 0 0 1 {x+w-r} {y+h}H{x+r}A{r} {r} 0 0 1 {x} {y+h-r}V{y+r}A{r} {r} 0 0 1 {x+r} {y}Z", None)

def line(d, width=64):
    return ("path", d, width)

def arrow_down(x, y0, y1):
    return line(f"M{x} {y0}V{y1}M{x-90} {y1-90}L{x} {y1}L{x+90} {y1-90}", 72)

GROUND = rect(40, 880, 880, 50, 25)

PICTOGRAMS = {
    # A hand, palm down, pressing on a limb from above
    "press": [arrow_down(480, 30, 260), g("pan_tool", (170, 330, 620, 420), rot=90), rect(40, 730, 880, 170, 85)],
    "no_touch": [g("do_not_touch", (40, 40, 880, 880))],
    # Smoke above, the person low beneath it
    "stay_low": [g("cloud", (120, 40, 720, 420)), g("man", (60, 640, 840, 230), rot=270), GROUND],
}

def placement(p):
    """The glyph's transform: fit, then turn or mirror inside its own box."""
    t = p
    inner = f"translate({-t['bx']} {C - t['by']})"
    turn = ""
    if t["rot"]:
        turn = f"translate({t['nw']/2} {t['nh']/2}) rotate({t['rot']}) translate({-t['bw']/2} {-t['bh']/2}) "
    mirror = f"translate({t['nw']} 0) scale(-1 1) " if t["flip"] else ""
    return f"translate({t['ox']:.1f} {t['oy']:.1f}) scale({t['s']:.4f}) {mirror}{turn}{inner}"

def svg(parts):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {C} {C}">']
    for p in parts:
        if p[0] == "glyph":
            out.append(f'<path transform="{placement(p[2])}" d="{p[1]}"/>')
        else:
            _, d, width = p
            if width:
                out.append(f'<path d="{d}" fill="none" stroke="#000" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>')
            else:
                out.append(f'<path d="{d}"/>')
    out.append("</svg>")
    return "\n".join(out) + "\n"

def vector(name, parts):
    a = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- Sprotyv pictogram "{name}", built by scripts/pictograms/build.py from Material Symbols (Apache 2.0) -->',
           f'<vector {a} android:width="48dp" android:height="48dp" android:viewportWidth="{C}" android:viewportHeight="{C}">']
    for p in parts:
        if p[0] == "glyph":
            t = p[2]
            # The same chain as placement(), one group per step, outermost first
            groups = [f'android:translateX="{t["ox"]:.1f}" android:translateY="{t["oy"]:.1f}"',
                      f'android:scaleX="{t["s"]:.4f}" android:scaleY="{t["s"]:.4f}"']
            if t["flip"]:
                groups += [f'android:translateX="{t["nw"]}"', 'android:scaleX="-1"']
            if t["rot"]:
                groups += [f'android:translateX="{t["nw"]/2}" android:translateY="{t["nh"]/2}"',
                           f'android:rotation="{t["rot"]}"',
                           f'android:translateX="{-t["bw"]/2}" android:translateY="{-t["bh"]/2}"']
            groups.append(f'android:translateX="{-t["bx"]}" android:translateY="{C - t["by"]}"')
            ind = "    "
            for gr in groups:
                out.append(f"{ind}<group {gr}>")
                ind += "    "
            out.append(f'{ind}<path android:fillColor="#FF000000" android:pathData="{p[1]}"/>')
            for _ in groups:
                ind = ind[:-4]
                out.append(f"{ind}</group>")
        else:
            _, d, width = p
            if width:
                out.append(f'    <path android:pathData="{d}" android:strokeColor="#FF000000" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>')
            else:
                out.append(f'    <path android:fillColor="#FF000000" android:pathData="{d}"/>')
    out.append("</vector>")
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    for name, parts in PICTOGRAMS.items():
        open(os.path.join(HERE, name + ".svg"), "w", encoding="utf-8").write(svg(parts))
        open(os.path.join(OUT, f"pict_{name}.xml"), "w", encoding="utf-8").write(vector(name, parts))
    print(len(PICTOGRAMS), "pictograms")
