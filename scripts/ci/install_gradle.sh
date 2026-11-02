#!/usr/bin/env bash
# Installs the Gradle named in gradle/wrapper/gradle-wrapper.properties, checked by its SHA-256,
# and puts it on the PATH of the following CI steps.
set -euo pipefail
props=gradle/wrapper/gradle-wrapper.properties
url=$(sed -n 's/^distributionUrl=//p' "$props" | sed 's/\\:/:/')
sha=$(sed -n 's/^distributionSha256Sum=//p' "$props")
[[ "$url" =~ ^https://services\.gradle\.org/distributions/gradle-([0-9.]+)-bin\.zip$ ]] || { echo "::error::Unexpected Gradle address"; exit 1; }
[[ "$sha" =~ ^[0-9a-f]{64}$ ]] || { echo "::error::Gradle checksum missing"; exit 1; }
version=${BASH_REMATCH[1]}
curl -sSfL -o "$RUNNER_TEMP/gradle.zip" "$url"
echo "$sha  $RUNNER_TEMP/gradle.zip" | sha256sum -c -
unzip -q "$RUNNER_TEMP/gradle.zip" -d "$RUNNER_TEMP"
echo "$RUNNER_TEMP/gradle-$version/bin" >> "$GITHUB_PATH"
