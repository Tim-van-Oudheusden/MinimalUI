#!/usr/bin/env bash
# Release version: reads versionName and versionCode from the app build file (default app/build.gradle.kts) and prints
# the release they name as key=value lines for $GITHUB_OUTPUT: version, tag (v<version>), prerelease (true for a
# -suffix version such as 0.2.0-beta.1) and version_code. Exits 1 unless the file has exactly one
# MAJOR.MINOR.PATCH[-suffix] versionName and exactly one numeric versionCode.
#
# Given PREVIOUS_FILE, the last release's build file, it also exits 1 unless versionCode is higher than that file's:
# Android refuses to install a lower versionCode over the release a user has, and updaters that compare versionCode
# (F-Droid, IzzyOnDroid, Obtainium) never offer an equal one (#437).
#
# Usage: release-version.sh [FILE [PREVIOUS_FILE]]
set -uo pipefail

file="${1:-app/build.gradle.kts}"
previous_file="${2:-}"

# Prints the one numeric versionCode in $1, or an ::error:: and returns 1.
version_code_of() {
  local codes
  mapfile -t codes < <(sed -nE 's/^[[:space:]]*versionCode = (.*[^[:space:]])[[:space:]]*$/\1/p' "$1")
  if [ "${#codes[@]}" -ne 1 ]; then
    echo "::error::Expected one 'versionCode = N' line in $1, found ${#codes[@]}"
    return 1
  fi
  if ! [[ "${codes[0]}" =~ ^[1-9][0-9]*$ ]]; then
    echo "::error::versionCode '${codes[0]}' in $1 is not a positive whole number"
    return 1
  fi
  echo "${codes[0]}"
}

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

if ! version_code=$(version_code_of "$file"); then
  echo "$version_code"
  exit 1
fi

echo "version=$version"
echo "tag=v$version"
if [[ "$version" == *-* ]]; then echo "prerelease=true"; else echo "prerelease=false"; fi
echo "version_code=$version_code"

if [ -n "$previous_file" ]; then
  if ! previous_code=$(version_code_of "$previous_file"); then
    echo "$previous_code"
    exit 1
  fi
  if [ "$version_code" -le "$previous_code" ]; then
    echo "::error::versionCode $version_code in $file is not higher than the last release's versionCode $previous_code. Android refuses a lower one and updaters skip an equal one: raise versionCode."
    exit 1
  fi
fi
