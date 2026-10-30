#!/usr/bin/env python3
"""
Monthly check of every outside address in content/src/main/assets/links.json (run by
.github/workflows/links.yml, never by the app). A gone page (404/410), a dead domain or a
moved address fails the run, so a person fixes the list; sites that refuse robots (403/429)
are only reported, as a person can still open them.
"""
import json, os, socket, sys, urllib.request, urllib.error

PATH = os.path.join(os.path.dirname(__file__), "..", "content", "src", "main", "assets", "links.json")
HEADERS = {"User-Agent": "Mozilla/5.0 (Android 14; Mobile) link check for an open-source safety app"}

def check(url):
    req = urllib.request.Request(url, headers=HEADERS, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            final = r.geturl()
            # Sent elsewhere on another domain: worth a look (a sold or hijacked domain looks like this)
            if final.split("/")[2].removeprefix("www.") != url.split("/")[2].removeprefix("www."):
                return "moved", final
            return "ok", r.status
    except urllib.error.HTTPError as e:
        if e.code in (401, 403, 405, 429, 503):
            return "refused", e.code
        return "broken", e.code
    except (urllib.error.URLError, socket.timeout, ConnectionError) as e:
        return "unreachable", str(getattr(e, "reason", e))

def main():
    links = json.load(open(PATH, encoding="utf-8"))
    bad = []
    for l in links:
        if not l["url"] or l["url"].startswith("https://t.me/"):
            continue
        state, info = check(l["url"])
        print(f"{state:12} {l['id']:24} {l['url']} {info}")
        if state in ("broken", "moved"):
            bad.append(l["id"])
    if bad:
        print("::error::Check these links: " + ", ".join(bad))
        sys.exit(1)

if __name__ == "__main__":
    main()
