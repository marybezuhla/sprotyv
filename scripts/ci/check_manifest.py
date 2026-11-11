#!/usr/bin/env python3
"""
Checks the merged manifest of the built APK, read with `aapt2 dump xmltree --file AndroidManifest.xml`:
no Android backup, not debuggable, no plain-text traffic, and no component open to other apps
unless it is on the list below with the reason. Anything else stops the build.

    aapt2 dump xmltree --file AndroidManifest.xml app.apk > manifest.txt
    python3 scripts/ci/check_manifest.py manifest.txt
"""
import re, sys

# Components other apps may reach, and why. A library adding one more stops the build until it is
# looked at and added here on purpose.
EXPORTED = {
    "ua.sprotyv.ui.MainActivity": "the launcher icon",
    "ua.sprotyv.LauncherNeutral": "the neutral launcher icon",
    "ua.sprotyv.ShareIn": "sharing a page into the app (off in trip, kids' and neutral modes)",
    "ua.sprotyv.ui.CountWidgetSetup": "the launcher sets up a widget",
    "ua.sprotyv.ui.TileWidgetSetup": "the launcher sets up a widget",
}
# Widgets: open only to the system (BIND_APPWIDGET)
WIDGET_PERMISSION = "android.permission.BIND_APPWIDGET"
# AndroidX's profile installer: open only to the shell (DUMP), never to apps
DUMP = "android.permission.DUMP"

ANDROID = "http://schemas.android.com/apk/res/android:"


def parse(lines):
    """Elements as (tag, attributes), with the depth they sit at."""
    out, stack = [], []
    for line in lines:
        m = re.match(r"^(\s*)(E|A): (.*)$", line)
        if not m:
            continue
        depth, kind, rest = len(m.group(1)), m.group(2), m.group(3)
        if kind == "E":
            tag = rest.split(" ")[0]
            while stack and stack[-1][0] >= depth:
                stack.pop()
            el = {"tag": tag, "attrs": {}, "parent": stack[-1][1] if stack else None}
            stack.append((depth, el))
            out.append(el)
        elif stack:
            a = re.match(r"^(\S+?)(\(0x[0-9a-f]+\))?=(.*)$", rest)
            if a:
                name = a.group(1).replace(ANDROID, "")
                value = a.group(3)
                raw = re.search(r'\(Raw: "([^"]*)"\)', value)
                value = raw.group(1) if raw else value.strip('"')
                stack[-1][1]["attrs"][name] = value
    return out


def main(path):
    els = parse(open(path, encoding="utf-8").read().splitlines())
    errors = []
    apps = [e for e in els if e["tag"] == "application"]
    if len(apps) != 1:
        errors.append("could not read <application>")
    else:
        a = apps[0]["attrs"]
        if a.get("allowBackup", "").lower() not in ("false", "0x0", "0"):
            errors.append("allowBackup is not false")
        if a.get("debuggable", "false").lower() not in ("false", "0x0", "0"):
            errors.append("the APK is debuggable")
        if a.get("usesCleartextTraffic", "false").lower() not in ("false", "0x0", "0"):
            errors.append("plain-text traffic is allowed")
    seen = 0
    for e in els:
        if e["tag"] not in ("activity", "activity-alias", "receiver", "service", "provider"):
            continue
        seen += 1
        a = e["attrs"]
        if a.get("exported", "false").lower() not in ("true", "0xffffffff", "-1"):
            continue
        name = a.get("name", "?")
        permission = a.get("permission", "")
        if name in EXPORTED or permission in (WIDGET_PERMISSION, DUMP):
            continue
        errors.append(f"open to other apps without a reason: {e['tag']} {name}")
    if seen == 0:
        errors.append("no components read: the listing is empty or unreadable")
    for err in errors:
        print(f"::error::Manifest: {err}")
    if errors:
        sys.exit(1)
    print(f"Manifest checked: {seen} components, backup off, not debuggable, no plain-text traffic")


if __name__ == "__main__":
    main(sys.argv[1])
