#!/usr/bin/env bash
# Fails if any text file in the repo contains an em-dash (U+2014).
#
# Everything in this repo is read by people: the README, UI strings, the
# accessibility description shown in TV settings. Binary files are skipped
# (grep -I), because decoding an image as text produces false matches.
# A line that genuinely needs the character can carry `allow-em-dash`.
set -euo pipefail
cd "$(dirname "$0")/.."

dash=$'\xe2\x80\x94'
found=0
while IFS= read -r -d '' file; do
  [ -f "$file" ] || continue
  grep -qI . "$file" 2>/dev/null || continue
  if matches=$(grep -n "$dash" "$file" | grep -v 'allow-em-dash'); then
    printf '%s\n' "$matches" | sed "s|^|$file:|"
    found=1
  fi
done < <(git ls-files -z --cached --others --exclude-standard)

if [ "$found" -ne 0 ]; then
  echo "Em-dashes found. Use a comma, colon, full stop, or a spaced hyphen instead." >&2
  exit 1
fi
echo "No em-dashes."
