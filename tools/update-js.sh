#!/usr/bin/env bash
# Updates the JavaScript libraries shipped in app/src/main/assets:
#   readability  Mozilla Readability (reader mode), latest release from npm
#   adblock      @ghostery/adblocker bundle and filter engine, built by tools/adblock
#
# Usage: tools/update-js.sh [--no-upgrade] [readability] [adblock]
#   With no library names, updates all of them.
#   --no-upgrade  adblock: keep the versions pinned in tools/adblock/package-lock.json and only
#                 rebuild (this still refreshes the filter lists in engine.bin).
#
# Needs curl, jq, tar, openssl and base64; adblock also needs node and npm.
# ClearURLs rules are data, not code: update them with tools/clearurls/update.sh.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
assets="$root/app/src/main/assets"

upgrade=true
targets=()
for arg in "$@"; do
  case "$arg" in
    --no-upgrade) upgrade=false ;;
    readability | adblock) targets+=("$arg") ;;
    -h | --help)
      sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg (try --help)" >&2
      exit 2
      ;;
  esac
done
((${#targets[@]})) || targets=(readability adblock)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

need() {
  for cmd in "$@"; do
    command -v "$cmd" > /dev/null || { echo "Missing required command: $cmd" >&2; exit 1; }
  done
}

update_readability() {
  need curl jq tar openssl base64
  local out="$assets/readability"

  echo "readability: checking npm for the latest @mozilla/readability"
  local meta version tarball integrity
  meta="$(curl -fsSL https://registry.npmjs.org/@mozilla/readability/latest)"
  version="$(jq -r .version <<< "$meta")"
  tarball="$(jq -r .dist.tarball <<< "$meta")"
  integrity="$(jq -r .dist.integrity <<< "$meta")"
  if [[ "$integrity" != sha512-* ]]; then
    echo "readability: unexpected integrity field: $integrity" >&2
    return 1
  fi

  curl -fsSL "$tarball" -o "$tmp/package.tgz"
  local actual
  actual="sha512-$(openssl dgst -sha512 -binary "$tmp/package.tgz" | base64 -w0)"
  if [[ "$actual" != "$integrity" ]]; then
    echo "readability: integrity mismatch for $tarball" >&2
    return 1
  fi

  tar -xzf "$tmp/package.tgz" -C "$tmp" package/Readability.js package/LICENSE.md
  mkdir -p "$out"
  if cmp -s "$tmp/package/Readability.js" "$out/Readability.js"; then
    echo "readability: already at the latest release ($version)"
  else
    echo "readability: updated to $version"
  fi
  mv "$tmp/package/Readability.js" "$out/Readability.js"
  mv "$tmp/package/LICENSE.md" "$out/LICENSE.md"
}

update_adblock() {
  need node npm
  # A broken node install (missing shared library, for example) fails here with a clear message.
  node --version > /dev/null || { echo "adblock: node doesn't run, fix it first" >&2; return 1; }
  local dir="$root/tools/adblock"
  (
    cd "$dir"
    if $upgrade; then
      echo "adblock: upgrading the build dependencies to their latest releases"
      npm install --save-dev --save-exact --no-audit --no-fund \
        @ghostery/adblocker@latest esbuild@latest tldts-experimental@latest
    else
      echo "adblock: installing the pinned build dependencies"
      npm ci --no-audit --no-fund
    fi
    # Bundles adblocker.js, downloads fresh filters into engine.bin and smoke-tests both.
    npm run --silent build
  )
  echo "adblock: versions now $(jq -c .devDependencies "$dir/package.json")"
}

for target in "${targets[@]}"; do
  "update_$target"
done

echo
echo "Done. Review the changes with: git -C \"$root\" diff --stat -- app/src/main/assets tools"
