#!/usr/bin/env bash
# Runs the A2A compatibility kit against a running agent for the JSON-RPC and HTTP+JSON bindings.
#
# Usage: run_tck.sh <agent-card-url> [kit options]
#
# The first run covers the MUST requirements only. The script exits with a non-zero status when one fails.
# The second run covers all requirement levels and writes the SHOULD and MAY findings to its report.
# Those findings do not change the exit status, as the kit does not treat them as incompatibility.
# Reports go to reports/must and reports/all next to this script.
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <agent-card-url> [kit options]" >&2
  exit 2
fi
card_url="$1"
shift
sut_host="${card_url%/.well-known/agent-card.json}"
sut_host="${sut_host%/}"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
kit_dir="$here/.cache/a2a-tck"
reports_dir="$here/reports"
kit_commit="$(sed -n 's|^a2a-tck @ git+.*@\([0-9a-f]\{40\}\)$|\1|p' "$here/requirements.txt")"
if [ -z "$kit_commit" ]; then
  echo "No a2a-tck commit in requirements.txt" >&2
  exit 2
fi

# On Windows, python and python3 can be Microsoft Store stubs that open an install prompt when run, so the
# launcher is tried first and python3 is never run there.
if [ -z "${PYTHON:-}" ]; then
  if command -v py >/dev/null 2>&1; then
    PYTHON="py -3"
  else
    PYTHON=python3
  fi
fi

if [ ! -d "$here/.venv" ]; then
  $PYTHON -m venv "$here/.venv"
fi
if [ -x "$here/.venv/bin/python" ]; then
  venv_python="$here/.venv/bin/python"
else
  venv_python="$here/.venv/Scripts/python"
fi
"$venv_python" -m pip install --quiet -r "$here/requirements.txt"

if [ ! -d "$kit_dir/.git" ]; then
  mkdir -p "$here/.cache"
  git clone --quiet https://github.com/a2aproject/a2a-tck.git "$kit_dir"
fi
git -C "$kit_dir" checkout --quiet "$kit_commit"

rm -rf "$reports_dir"
mkdir -p "$reports_dir"
status=0

run_kit() {
  name="$1"
  shift
  echo "== a2a-tck: $name =="
  rm -rf "$kit_dir/reports"
  (cd "$kit_dir" && "$venv_python" run_tck.py --sut-host "$sut_host" --transport jsonrpc,http_json "$@") || status=$?
  if [ -d "$kit_dir/reports" ]; then
    mv "$kit_dir/reports" "$reports_dir/$name"
  fi
}

run_kit must --level must "$@"
if [ "$status" -ne 0 ]; then
  echo "MUST requirements failed. See $reports_dir/must/compatibility.html" >&2
  exit "$status"
fi
run_kit all "$@"
if [ "$status" -ne 0 ]; then
  echo "SHOULD or MAY findings: see $reports_dir/all/compatibility.html" >&2
fi
echo "a2a-tck MUST requirements passed. Reports are in $reports_dir"
exit 0
