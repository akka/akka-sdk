#!/usr/bin/env bash

# Run Vale locally against docs/src, matching the CI configuration in
# .github/workflows/documentation.yml.
#
# Usage (from the repo root or anywhere inside it):
#   docs/bin/vale.sh                    # default: error-level, same as CI
#   docs/bin/vale.sh --warning          # also show warnings
#   docs/bin/vale.sh --suggestion       # show warnings and suggestions
#   docs/bin/vale.sh docs/src/modules/sdk/pages/agents.adoc   # lint one file
#
# Any other arguments are forwarded to `vale`.

set -euo pipefail

# Version pinned in .github/workflows/documentation.yml
VALE_REQUIRED_VERSION="3.19.0"

# Resolve repo root from this script's location so the script works no matter
# where the user invokes it from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m==>\033[0m %s\n' "$*" >&2; }
err()  { printf '\033[1;31m==>\033[0m %s\n' "$*" >&2; }

is_macos() { [[ "$(uname -s)" == "Darwin" ]]; }

ensure_vale() {
  if command -v vale >/dev/null 2>&1; then
    local installed
    installed="$(vale --version | awk '{print $NF}')"
    if [[ "$installed" != "$VALE_REQUIRED_VERSION" ]]; then
      warn "Vale $installed is installed; CI pins $VALE_REQUIRED_VERSION. Results may differ."
    fi
    return
  fi

  if ! is_macos; then
    err "vale is not installed. Install it from https://vale.sh/docs/install and rerun."
    exit 1
  fi

  if ! command -v brew >/dev/null 2>&1; then
    err "vale is not installed and Homebrew is not available. Install Homebrew from https://brew.sh or install vale manually."
    exit 1
  fi

  log "Installing vale via Homebrew"
  brew install vale
}

ensure_asciidoctor() {
  if command -v asciidoctor >/dev/null 2>&1; then
    return
  fi

  if is_macos && command -v brew >/dev/null 2>&1; then
    log "Installing asciidoctor via Homebrew"
    brew install asciidoctor
    return
  fi

  if command -v gem >/dev/null 2>&1; then
    log "Installing asciidoctor via gem"
    gem install asciidoctor
    return
  fi

  err "asciidoctor is required to lint .adoc files. Install it via 'brew install asciidoctor' or 'gem install asciidoctor'."
  exit 1
}

# Translate friendly flags to Vale's --minAlertLevel; leave everything else alone.
VALE_ARGS=()
TARGETS=()
for arg in "$@"; do
  case "$arg" in
    --warning|-w)
      VALE_ARGS+=("--minAlertLevel=warning")
      ;;
    --suggestion|-s)
      VALE_ARGS+=("--minAlertLevel=suggestion")
      ;;
    -h|--help)
      sed -n '3,13p' "$0"
      exit 0
      ;;
    -*)
      VALE_ARGS+=("$arg")
      ;;
    *)
      TARGETS+=("$arg")
      ;;
  esac
done

if [[ ${#TARGETS[@]} -eq 0 ]]; then
  TARGETS=(docs/src)
fi

ensure_vale
ensure_asciidoctor

log "Running: vale --config=docs/.vale.ini ${VALE_ARGS[*]:-} ${TARGETS[*]}"
# Guard the array expansions: on bash 3.2 (macOS default) with `set -u`,
# expanding an empty array via `"${arr[@]}"` raises "unbound variable".
exec vale --config=docs/.vale.ini \
  ${VALE_ARGS[@]+"${VALE_ARGS[@]}"} \
  ${TARGETS[@]+"${TARGETS[@]}"}
