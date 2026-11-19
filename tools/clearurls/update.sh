#!/usr/bin/env bash
# Downloads the latest ClearURLs rules (LGPL-3.0, https://gitlab.com/ClearURLs/rules) into the
# app assets, checking them against the published SHA-256.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
out="$root/app/src/main/assets/clearurls"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -fsSL https://rules2.clearurls.xyz/data.minify.json -o "$tmp/data.json"
expected="$(curl -fsSL https://rules2.clearurls.xyz/rules.minify.hash | tr -d '[:space:]')"
actual="$(sha256sum "$tmp/data.json" | cut -d' ' -f1)"
if [[ "$expected" != "$actual" ]]; then
  echo "Hash mismatch: expected $expected, got $actual" >&2
  exit 1
fi

mkdir -p "$out"
mv "$tmp/data.json" "$out/data.min.json"
echo "Updated $out/data.min.json ($(wc -c < "$out/data.min.json") bytes)"
