#!/usr/bin/env bash
# Release version: reads versionName from the app build file (default app/build.gradle.kts) and prints the release it
# names as key=value lines for $GITHUB_OUTPUT: version, tag (v<version>) and prerelease (true for a -suffix version
# such as 0.2.0-beta.1). Exits 1 unless the file has exactly one MAJOR.MINOR.PATCH[-suffix] versionName.
set -uo pipefail

file="${1:-app/build.gradle.kts}"

mapfile -t names < <(sed -nE 's/^[[:space:]]*versionName = "([^"]*)"[[:space:]]*$/\1/p' "$file")
if [ "${#names[@]}" -ne 1 ]; then
  echo "::error::Expected one 'versionName = \"...\"' line in $file, found ${#names[@]}"
  exit 1
fi

version="${names[0]}"
if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "::error::versionName '$version' in $file is not MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-suffix"
  exit 1
fi

echo "version=$version"
echo "tag=v$version"
if [[ "$version" == *-* ]]; then echo "prerelease=true"; else echo "prerelease=false"; fi
