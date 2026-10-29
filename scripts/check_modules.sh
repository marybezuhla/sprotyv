#!/usr/bin/env bash
# Module rules, read from the source on every build without running any build code.
# Defence in depth: the real guarantee of "no network" in release 1.0 is the missing INTERNET
# permission, which Android itself enforces and CI checks in the built APK. The dependency rule
# is also checked on the resolved dependency graph after the build (build.yml).
#  1. Only :app may depend on :personal (the owner's encrypted data).
#  2. Release 1.0 has no network code at all.
# A failed rule stops the build.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "::error::$1"; exit 1; }

modules=(app content personal reach)
for m in "${modules[@]}"; do
  [ -f "$m/build.gradle.kts" ] || fail "Module $m is missing"
done

# Build logic only in the reviewed Kotlin build files: no Groovy builds, buildSrc,
# convention plugins or version catalogs that could add a dependency out of sight
if find . \( -path ./.git -o -name .gradle -o -name build \) -prune -o -type f \( -name "*.gradle" -o -name "*.versions.toml" \) -print | grep -q .; then
  fail "Only build.gradle.kts files are allowed (no Groovy builds or version catalogs)"
fi
[ -e buildSrc ] && fail "buildSrc is not allowed"
[ -e build-logic ] && fail "build-logic is not allowed"
extra=$(find . \( -path ./.git -o -name .gradle -o -name build \) -prune -o -type f -name "*.gradle.kts" -print | grep -vE '^\./(settings\.gradle\.kts|build\.gradle\.kts|(app|content|personal|reach)/build\.gradle\.kts)$' || true)
[ -z "$extra" ] || fail "Unexpected build file: $extra"

# 1. Who mentions :personal: only app and settings may, in any spelling
while IFS= read -r f; do
  case "$f" in
    ./app/build.gradle.kts|./settings.gradle.kts) ;;
    *) fail "$f refers to :personal; only app may depend on it" ;;
  esac
done < <(grep -rlE --include=*.gradle.kts '":personal"|'"'"':personal'"'"'|projects\.personal' . || true)

# No source outside app/ and personal/ may use the personal module
if grep -rnE --include=*.kt --include=*.java 'ua\.sprotyv\.personal' content reach >/dev/null; then
  grep -rnE --include=*.kt --include=*.java 'ua\.sprotyv\.personal' content reach
  fail "content or reach uses personal data"
fi

# 2. No network code in release 1.0, in Kotlin or Java
pattern='java\.net\.|javax\.net\.|java\.nio\.channels\.(Socket|ServerSocket|Datagram)|HttpURLConnection|openConnection|openStream|DatagramSocket|okhttp|ktor|retrofit|cronet|WebView|DownloadManager'
if grep -rnE --include=*.kt --include=*.java "$pattern" app content personal reach >/dev/null; then
  grep -rnE --include=*.kt --include=*.java "$pattern" app content personal reach
  fail "Network code found; release 1.0 has no network"
fi
if grep -rnEi --include=*.gradle.kts 'okhttp|ktor|retrofit|cronet|volley' . >/dev/null; then
  grep -rnEi --include=*.gradle.kts 'okhttp|ktor|retrofit|cronet|volley' .
  fail "A network library was added"
fi

echo "Module rules OK"
