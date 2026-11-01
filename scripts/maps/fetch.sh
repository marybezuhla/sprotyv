#!/usr/bin/env bash
# Puts the built-in map into the app: the files of the release named in maps.lock, each checked
# against the SHA-256 written there. Nothing else is trusted: files are checked in a temporary
# folder and only then moved into the app, so a file that fails never stays behind.
set -euo pipefail
cd "$(dirname "$0")/../.."
lock=maps.lock
if [ ! -f "$lock" ]; then
  # On CI a missing lock is a mistake (a release without the map); locally it is allowed
  [ -n "${CI:-}" ] && { echo "No maps.lock"; exit 1; }
  echo "No maps.lock: the app is built without the built-in map"; exit 0
fi
tag=$(sed -n 's/^tag //p' "$lock")
[[ "$tag" =~ ^maps-[0-9]{8}-[0-9]+$ ]] || { echo "Bad tag in maps.lock"; exit 1; }
pattern='^[0-9a-f]{64}  [A-Za-z0-9_-][A-Za-z0-9._-]*$'
lines=$(grep -E "$pattern" "$lock" || true)
[ -n "$lines" ] || { echo "No files in maps.lock"; exit 1; }
out=app/src/main/assets/maps
rm -rf "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
while read -r sum file; do
  curl -sSfL --retry 3 -o "$tmp/$file" "https://github.com/Marybezu/sprotyv/releases/download/$tag/$file"
done <<< "$lines"
(cd "$tmp" && sha256sum -c - <<< "$lines")
mkdir -p "$out"
while read -r sum file; do mv "$tmp/$file" "$out/$file"; done <<< "$lines"
ls -la "$out"
