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
    "call": (120, 120, 720, 720), "do_not_touch": (38, 40, 826, 880), "steps": (84, 90, 796, 790),
    "door_front": (120, 120, 720, 720), "airline_seat_flat": (80, 280, 800, 400), "pan_tool": (64, 40, 777, 880),
    "heat": (200, 119, 560, 720), "man": (340, 80, 280, 800), "valve": (160, 120, 640, 711),
    "block": (80, 80, 800, 800), "window": (120, 120, 720, 720), "directions_walk": (232, 60, 521, 860),
    "house": (52, 135, 856, 665),
}

def glyph_path(name):
    svg = open(os.path.join(HERE, "material", name + ".svg"), encoding="utf-8").read()
    return re.search(r' d="([^"]+)"', svg).group(1)

def g(name, box, flip=False):
    """A glyph fitted into box (x, y, w, h), centred, keeping its shape."""
    bx, by, bw, bh = BOUNDS[name]
    x, y, w, h = box
    s = min(w / bw, h / bh)
    ox, oy = x + (w - bw * s) / 2, y + (h - bh * s) / 2
    return ("glyph", glyph_path(name), ox, oy, s, bx, by, flip, bw)

def rect(x, y, w, h, r):
    return ("path", f"M{x+r} {y}H{x+w-r}A{r} {r} 0 0 1 {x+w} {y+r}V{y+h-r}A{r} {r} 0 0 1 {x+w-r} {y+h}H{x+r}A{r} {r} 0 0 1 {x} {y+h-r}V{y+r}A{r} {r} 0 0 1 {x+r} {y}Z", None)

def line(d, width=64):
    return ("path", d, width)

DOWN = lambda x: line(f"M{x} 100V420M{x-80} 340L{x} 420L{x+80} 340")

PICTOGRAMS = {
    "call": [g("call", (40, 40, 880, 880))],
    "no_touch": [g("do_not_touch", (40, 40, 880, 880))],
    "shoes": [g("steps", (40, 40, 880, 880))],
    "close_door": [g("door_front", (60, 60, 840, 840))],
    "lie_down": [g("airline_seat_flat", (40, 200, 880, 560))],
    # A hand pressing down on a limb
    "press": [g("pan_tool", (250, 20, 460, 580)), DOWN(130), DOWN(830), rect(40, 680, 880, 170, 85)],
    # The blast outside, two walls, then the person
    "two_walls": [g("heat", (0, 220, 210, 520)), rect(280, 100, 96, 760, 32), rect(440, 100, 96, 760, 32), g("man", (610, 100, 320, 760))],
    # Walk in under a roof
    "shelter": [g("directions_walk", (0, 260, 330, 600)), g("house", (350, 150, 610, 720))],
    # Below the smoke
    "stay_low": [line("M80 190Q180 110 280 190T480 190T680 190T880 190"), line("M80 350Q180 270 280 350T480 350T680 350T880 350"),
                 g("airline_seat_flat", (40, 480, 880, 440))],
    # Walk back the way you came
    "step_back": [g("directions_walk", (360, 40, 560, 880), flip=True), line("M300 820H70M170 720L70 820L170 920")],
    "gas_off": [g("valve", (0, 0, 560, 560)), g("block", (580, 580, 380, 380))],
    "no_window": [g("window", (0, 0, 560, 560)), g("block", (580, 580, 380, 380))],
}

def svg(parts):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {C} {C}">']
    for p in parts:
        if p[0] == "glyph":
            _, d, ox, oy, s, bx, by, flip, bw = p
            sx = -s if flip else s
            ox2 = ox + bw * s if flip else ox
            out.append(f'<path transform="translate({ox2:.1f} {oy:.1f}) scale({sx:.4f} {s:.4f}) translate({-bx} {C-by})" d="{d}"/>')
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
            _, d, ox, oy, s, bx, by, flip, bw = p
            sx = -s if flip else s
            ox2 = ox + bw * s if flip else ox
            out += [f'    <group android:translateX="{ox2:.1f}" android:translateY="{oy:.1f}">',
                    f'        <group android:scaleX="{sx:.4f}" android:scaleY="{s:.4f}">',
                    f'            <group android:translateX="{-bx}" android:translateY="{C-by}">',
                    f'                <path android:fillColor="#FF000000" android:pathData="{d}"/>',
                    '            </group>', '        </group>', '    </group>']
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
