#!/usr/bin/env bash
set -euo pipefail

release_tag="${1:?Usage: update-optimize-version.sh <release-tag>}"
if [[ ! "$release_tag" =~ ^v[0-9][0-9A-Za-z._-]*$ ]]; then
  echo "Invalid Optimize release tag: ${release_tag}" >&2
  exit 1
fi

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
action="${repo_dir}/.github/actions/checkout-optimize-docs/action.yml"
action_tmp="$(mktemp "${action}.XXXXXX")"
trap 'rm -f "$action_tmp"' EXIT

# Require a single checkout ref so changes to the action cannot silently update other checkouts.
awk -v tag="$release_tag" '
  /^[[:space:]]*ref:/ {
    sub(/ref:.*/, "ref: " tag)
    count++
  }
  { print }
  END {
    if (count != 1) {
      print "Expected one Optimize checkout ref, found " count > "/dev/stderr"
      exit 1
    }
  }
' "$action" > "$action_tmp"
cat "$action_tmp" > "$action"
