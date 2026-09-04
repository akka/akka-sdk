#!/usr/bin/env bash

# Sort docs/styles/config/vocabularies/Akka/accept.txt (and reject.txt) into a
# stable case-insensitive order, treating Vale regex constructs as if they were
# the plain word. Also removes duplicate lines.
#
# The sort key for each line is derived by:
#   [Aa]kka          -> akka        (character class collapsed to lowercase letter)
#   (?i)API          -> api         ((?i) case-insensitive flag dropped)
#   AZs?             -> azs         (trailing regex quantifier dropped)
#   [Oo]pen[Aa]i     -> openai
#
# Usage:
#   docs/bin/sort-vale-vocab.sh              # sorts accept.txt in place
#   docs/bin/sort-vale-vocab.sh --check      # exit non-zero if not sorted
#   docs/bin/sort-vale-vocab.sh path/to.txt  # sort a specific file

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEFAULT_FILE="$REPO_ROOT/docs/styles/config/vocabularies/Akka/accept.txt"

CHECK=0
FILES=()
for arg in "$@"; do
  case "$arg" in
    --check) CHECK=1 ;;
    -h|--help)
      sed -n '3,17p' "$0"
      exit 0
      ;;
    *) FILES+=("$arg") ;;
  esac
done

if [[ ${#FILES[@]} -eq 0 ]]; then
  FILES=("$DEFAULT_FILE")
fi

sort_one() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "sort-vale-vocab: file not found: $file" >&2
    exit 1
  fi

  local sorted
  sorted="$(python3 - "$file" <<'PY'
import re
import sys

path = sys.argv[1]

with open(path, "r", encoding="utf-8") as f:
    lines = [
        ln for ln in f.read().splitlines()
        if ln.strip() and not ln.lstrip().startswith("#")
    ]

def sort_key(line: str) -> tuple:
    key = line
    # (?i) case-insensitive flag: drop it
    key = key.replace("(?i)", "")
    # [Aa] style character classes: keep the first letter, lowercased
    key = re.sub(r"\[([A-Za-z])[A-Za-z]\]", lambda m: m.group(1).lower(), key)
    # Trailing regex quantifiers on individual chars: strip
    key = re.sub(r"[?+*]", "", key)
    return (key.lower(), line)

lines.sort(key=sort_key)
# Remove duplicate lines, keeping the first occurrence.
lines = list(dict.fromkeys(lines))
sys.stdout.write("\n".join(lines) + "\n")
PY
)"

  # Command substitution strips trailing newlines; restore one.
  if [[ $CHECK -eq 1 ]]; then
    if ! diff -u "$file" <(printf '%s\n' "$sorted") >/dev/null; then
      echo "sort-vale-vocab: $file is not sorted or has duplicates. Run: docs/bin/sort-vale-vocab.sh" >&2
      diff -u "$file" <(printf '%s\n' "$sorted") >&2 || true
      exit 1
    fi
    echo "sort-vale-vocab: $file is sorted."
  else
    printf '%s\n' "$sorted" > "$file"
    echo "sort-vale-vocab: sorted $file"
  fi
}

for f in "${FILES[@]}"; do
  sort_one "$f"
done
