#!/usr/bin/env python3
"""
The pictures inside cards, redrawn in the pictograms' solid style from the guide's figures
(«Довідник виживання» 2.3, CC BY-SA 4.0: the same content, new drawing). Glyphs: Material
Symbols (Apache 2.0), copied unchanged in material/.

Each picture is up to three layers, tinted by the app: the drawing (text colour), what is safe
(the theme's main colour) and what is dangerous or bleeding (red: red means danger, nothing else).
The words are not inside the picture but under it, as text: large, translated, read by TalkBack.

    python3 scripts/pictograms/figures.py
      -> app/src/main/res/drawable/fig_<name>[_safe|_danger].xml and scripts/pictograms/fig_<name>.svg
"""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable")
W, H = 960, 480

# Drawn bounds of each glyph in its own coordinates (x, y, w, h), measured once in a browser
GLYPHS = {
    "material/man": (340, -880, 280, 800),
    "material/destruction": (105, -877, 735, 797),
    "material/check": (166, -704, 627, 446),
    "material/close": (219, -741, 522, 522),
    "material/skull": (80, -880, 800, 800),
    "material/flag": (200, -800, 600, 680),
    "material/pan_tool": (64, -920, 777, 880),
}

def load(name):
    svg = open(os.path.join(HERE, name + ".svg"), encoding="utf-8").read()
    return [(m.group(2), "evenodd" in m.group(1) + m.group(3)) for m in re.finditer(r'<path([^>]*?) d="([^"]+)"([^>]*)>', svg)]

def g(name, box, rot=0):
    bx, by, bw, bh = GLYPHS[name]
    nw, nh = (bh, bw) if rot in (90, 270) else (bw, bh)
    x, y, w, h = box
    s = min(w / nw, h / nh)
    return ("glyph", load(name), dict(ox=x + (w - nw * s) / 2, oy=y + (h - nh * s) / 2, s=s, bx=bx, by=by, bw=bw, bh=bh, nw=nw, nh=nh, rot=rot))

def shape(d, alpha=1.0):
    return ("fill", d, alpha)

def stroke(d, width):
    return ("stroke", d, width)

def rect(x, y, w, h, r=0, alpha=1.0):
    if not r:
        return shape(f"M{x} {y}H{x+w}V{y+h}H{x}Z", alpha)
    return shape(f"M{x+r} {y}H{x+w-r}A{r} {r} 0 0 1 {x+w} {y+r}V{y+h-r}A{r} {r} 0 0 1 {x+w-r} {y+h}H{x+r}A{r} {r} 0 0 1 {x} {y+h-r}V{y+r}A{r} {r} 0 0 1 {x+r} {y}Z", alpha)

def circle(cx, cy, r):
    return shape(f"M{cx-r} {cy}A{r} {r} 0 1 0 {cx+r} {cy}A{r} {r} 0 1 0 {cx-r} {cy}Z")

GROUND = rect(20, 432, 920, 24, 12)

FIGURES = {
    # 3.1: flat on the ground, feet towards the blast
    "prone": {
        "base": [
            circle(200, 360, 46),
            stroke("M290 372H620", 104),                 # body, face down
            stroke("M620 380L880 392", 64),              # legs towards the blast
            stroke("M300 340L220 280L150 320", 50),      # arms over the head and neck
            GROUND,
        ],
        "danger": [shape("M820 80L838 146L898 112L864 172L930 190L864 208L898 268L838 234L820 300L802 234L742 268L776 208L710 190L776 172L742 112L802 146Z")],
    },
    # 3.2: the two-wall rule on a flat's plan
    "two_walls": {
        "base": [
            # outer walls, with gaps where the windows are
            stroke("M120 40H60V440H900V40H760M560 40H400M200 40H120", 28),
            stroke("M200 40H400M560 40H760", 8),
            # inner walls: two rooms at the windows, the corridor between, the bathroom below
            stroke("M330 40V150M330 220V320M630 40V150M630 220V320M330 320H420M540 320H630V440M630 320H900", 24),
        ],
        "safe": [rect(346, 54, 270, 254, 16, 0.28), rect(644, 334, 242, 92, 16, 0.28),
                 g("material/check", (420, 130, 120, 100)), g("material/check", (705, 340, 120, 80))],
        "danger": [g("material/close", (140, 140, 110, 110)), g("material/close", (710, 140, 110, 110))],
    },
    # 5.2: how mined ground is marked, official and home-made
    "mine_marks": {
        "base": [
            stroke("M400 120L560 380M560 120L400 380", 44),
            g("material/flag", (700, 60, 200, 300)),
            circle(700, 410, 26), circle(770, 418, 22), circle(850, 410, 26),
        ],
        "danger": [stroke("M160 70L280 390H40Z", 36), g("material/skull", (110, 230, 100, 110))],
    },
    # 7.2: the tourniquet, high on the upper arm, a hand's width above the wound. A person's head
    # and shoulder at the left and an open hand at the right, so the limb reads as an arm; the
    # tourniquet itself is in the safe colour so it stands apart from the arm
    "tourniquet": {
        "base": [
            circle(100, 96, 64),                                          # head
            shape("M30 470V230Q30 170 100 170H200Q240 170 240 215V470Z"),  # shoulder and chest
            shape("M220 186L530 214Q560 220 560 258Q560 296 530 302L220 330Z"),   # upper arm
            shape("M520 222L790 240Q800 260 790 282L520 296Z"),           # forearm
            circle(530, 258, 44),                                         # elbow
            g("material/pan_tool", (770, 150, 190, 230), rot=90),         # the open hand
        ],
        "safe": [
            rect(318, 172, 40, 176, 12),                                  # the band, around the arm
            stroke("M286 140L392 196", 26),                               # the windlass stick
            stroke("M358 410H462M358 386V434M462 386V434", 16),           # a hand's width
        ],
        "danger": [circle(470, 262, 30), stroke("M470 292C476 320 486 340 478 372", 14)],
    },
    # 7.4: the recovery position, seen from above
    "recovery": {
        "base": [
            circle(140, 210, 58),
            stroke("M260 210H590", 120),                 # body
            stroke("M600 210H900", 72),                  # the lower leg, straight
            stroke("M590 230L700 340L880 370", 72),      # the upper knee bent forward, resting on the ground
            stroke("M300 170L250 70L170 110", 54),       # hand under the cheek
            stroke("M340 250L310 410", 54),              # the other arm, forward
        ],
    },
    # 7.7: chest compressions
    "cpr": {
        "base": [
            circle(150, 378, 44), stroke("M230 390H840", 76), GROUND,   # the person on their back
            circle(440, 82, 46),                                        # the helper, kneeling at the side
            stroke("M600 270L460 160", 96),
            stroke("M600 270L540 370", 84),
            stroke("M460 165L400 340", 48),                             # straight arms, down on the chest
        ],
        "danger": [circle(400, 390, 22)],
    },
}

def placement(t):
    turn = f"translate({t['nw']/2} {t['nh']/2}) rotate({t['rot']}) translate({-t['bw']/2} {-t['bh']/2}) " if t["rot"] else ""
    return f"translate({t['ox']:.1f} {t['oy']:.1f}) scale({t['s']:.4f}) {turn}translate({-t['bx']} {-t['by']})"

def svg_parts(parts, colour):
    out = []
    for p in parts:
        if p[0] == "glyph":
            for d, eo in p[1]:
                rule = ' fill-rule="evenodd"' if eo else ""
                out.append(f'<path transform="{placement(p[2])}" fill="{colour}"{rule} d="{d}"/>')
        elif p[0] == "fill":
            out.append(f'<path fill="{colour}" fill-opacity="{p[2]}" d="{p[1]}"/>')
        else:
            out.append(f'<path d="{p[1]}" fill="none" stroke="{colour}" stroke-width="{p[2]}" stroke-linecap="round" stroke-linejoin="round"/>')
    return out

def vector(parts):
    a = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Built by scripts/pictograms/figures.py; tinted by the app -->',
           f'<vector {a} android:width="{W // 4}dp" android:height="{H // 4}dp" android:viewportWidth="{W}" android:viewportHeight="{H}">']
    for p in parts:
        if p[0] == "glyph":
            t = p[2]
            groups = [f'android:translateX="{t["ox"]:.1f}" android:translateY="{t["oy"]:.1f}"',
                      f'android:scaleX="{t["s"]:.4f}" android:scaleY="{t["s"]:.4f}"']
            if t["rot"]:
                groups += [f'android:translateX="{t["nw"]/2}" android:translateY="{t["nh"]/2}"',
                           f'android:rotation="{t["rot"]}"',
                           f'android:translateX="{-t["bw"]/2}" android:translateY="{-t["bh"]/2}"']
            groups.append(f'android:translateX="{-t["bx"]}" android:translateY="{-t["by"]}"')
            ind = "    "
            for gr in groups:
                out.append(f"{ind}<group {gr}>")
                ind += "    "
            for d, eo in p[1]:
                rule = ' android:fillType="evenOdd"' if eo else ""
                out.append(f'{ind}<path android:fillColor="#FF000000"{rule} android:pathData="{d}"/>')
            for _ in groups:
                ind = ind[:-4]
                out.append(f"{ind}</group>")
        elif p[0] == "fill":
            out.append(f'    <path android:fillColor="#FF000000" android:fillAlpha="{p[2]}" android:pathData="{p[1]}"/>')
        else:
            out.append(f'    <path android:pathData="{p[1]}" android:strokeColor="#FF000000" android:strokeWidth="{p[2]}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>')
    out.append("</vector>")
    return "\n".join(out) + "\n"

COLOURS = {"base": "#1d1d1f", "safe": "#2f5fbf", "danger": "#c62828"}

if __name__ == "__main__":
    for name, layers in FIGURES.items():
        preview = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">']
        for layer, parts in layers.items():
            suffix = "" if layer == "base" else "_" + layer
            open(os.path.join(OUT, f"fig_{name}{suffix}.xml"), "w", encoding="utf-8").write(vector(parts))
            preview += svg_parts(parts, COLOURS[layer])
        open(os.path.join(HERE, f"fig_{name}.svg"), "w", encoding="utf-8").write("\n".join(preview + ["</svg>"]) + "\n")
    print(len(FIGURES), "figures")
