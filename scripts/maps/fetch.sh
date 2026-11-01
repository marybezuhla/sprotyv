#!/usr/bin/env bash
# Puts the built-in maps into the app: the files of the release named in maps.lock, each checked
# against the SHA-256 written there. Nothing else is trusted; any mismatch stops the build.
set -euo pipefail
cd "$(dirname "$0")/../.."
lock=maps.lock
[ -f "$lock" ] || { echo "No maps.lock: the app is built without the built-in map"; exit 0; }
tag=$(sed -n 's/^tag //p' "$lock")
[[ "$tag" =~ ^maps-[0-9]{8}-[0-9]+$ ]] || { echo "Bad tag in maps.lock"; exit 1; }
out=app/src/main/assets/maps
rm -rf "$out" && mkdir -p "$out"
grep -E '^[0-9a-f]{64}  [A-Za-z0-9._-]+$' "$lock" | while read -r sum file; do
  curl -sSfL --retry 3 -o "$out/$file" "https://github.com/Marybezu/sprotyv/releases/download/$tag/$file"
done
(cd "$out" && grep -E '^[0-9a-f]{64}  [A-Za-z0-9._-]+$' "../../../../../$lock" | sha256sum -c -)
ls -la "$out"
