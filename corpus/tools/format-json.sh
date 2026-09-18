#!/usr/bin/env bash
set -euo pipefail

check=false
if [[ "${1:-}" == "--check" ]]; then
  check=true
  shift
fi

if (($#)); then
  files=("$@")
else
  mapfile -d '' files < <(find . -type f -name '*.json' -not -path './.git/*' -print0)
fi

failed=false
for file in "${files[@]}"; do
  formatted=$(mktemp)
  jq . "$file" > "$formatted"
  if $check; then
    if ! cmp -s "$file" "$formatted"; then
      printf 'not jq-formatted: %s\n' "$file" >&2
      failed=true
    fi
    rm -f "$formatted"
  else
    mv "$formatted" "$file"
  fi
done

if $failed; then
  exit 1
fi
