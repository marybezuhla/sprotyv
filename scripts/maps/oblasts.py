#!/usr/bin/env python3
"""
The oblasts of Ukraine, found in the OpenStreetMap data itself (administrative boundaries of level
4 with an ISO 3166-2 code "UA-.."): for each, a bounding box for `osmium extract` and its names.

    python3 oblasts.py adm.geojsonseq extracts.json oblasts.json
"""
import json, sys

src, extracts_out, index_out = sys.argv[1:4]
found = {}
with open(src, encoding="utf-8") as f:
    for line in f:
        line = line.strip().lstrip("\x1e")
        if not line:
            continue
        feat = json.loads(line)
        p = feat.get("properties", {})
        code = p.get("ISO3166-2", "")
        if p.get("admin_level") != "4" or not code.startswith("UA-"):
            continue
        xs, ys = [], []
        def walk(c):
            if isinstance(c[0], (int, float)):
                xs.append(c[0]); ys.append(c[1])
            else:
                for x in c: walk(x)
        walk(feat["geometry"]["coordinates"])
        box = [min(xs), min(ys), max(xs), max(ys)]
        old = found.get(code)
        if old:  # several pieces of one oblast: one box around all
            box = [min(old["bbox"][0], box[0]), min(old["bbox"][1], box[1]), max(old["bbox"][2], box[2]), max(old["bbox"][3], box[3])]
        found[code] = {"id": code, "uk": p.get("name:uk") or p.get("name", code), "en": p.get("name:en") or p.get("name", code), "bbox": [round(v, 4) for v in box]}

assert len(found) >= 20, f"only {len(found)} oblasts found"
oblasts = sorted(found.values(), key=lambda o: o["uk"])
# A small margin, so roads on the edge are not cut
extracts = {"extracts": [{"output": o["id"] + ".osm.pbf", "bbox": [o["bbox"][0] - 0.05, o["bbox"][1] - 0.05, o["bbox"][2] + 0.05, o["bbox"][3] + 0.05]} for o in oblasts]}
json.dump(extracts, open(extracts_out, "w"), indent=1)
json.dump(oblasts, open(index_out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(oblasts), "oblasts")
