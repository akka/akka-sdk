#!/bin/bash
#
#  Copyright 2024 Lightbend Inc. All rights reserved. MIT license.
#  Forked from install-node:
#  https://github.com/vercel/install-node/blob/066fc811ab18d21f3d851c4b0ec171bf6320c020/install.sh
#  Copyright (c) 2017 Vercel, Inc. MIT license.
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:

# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

# Usage:
# 
# `install-cli.sh` is a simple one-liner shell script to
# Install the official Akka Command Line Interface
#
#   $ curl -sL https://doc.akka.io/install-cli.sh | bash
#
# Options may be passed to the shell script with `-s --`:
#
#   $ curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix=$HOME --version=3.0.1 --verbose
#   $ curl -sL https://doc.akka.io/install-cli.sh | bash -s -- -P $HOME -v 3.0.1 -V
#
set -euo pipefail

BOLD="$(tput bold 2>/dev/null || echo '')"
GREY="$(tput setaf 0 2>/dev/null || echo '')"
UNDERLINE="$(tput smul 2>/dev/null || echo '')"
RED="$(tput setaf 1 2>/dev/null || echo '')"
GREEN="$(tput setaf 2 2>/dev/null || echo '')"
YELLOW="$(tput setaf 3 2>/dev/null || echo '')"
BLUE="$(tput setaf 4 2>/dev/null || echo '')"
MAGENTA="$(tput setaf 5 2>/dev/null || echo '')"
CYAN="$(tput setaf 6 2>/dev/null || echo '')"
NO_COLOR="$(tput sgr0 2>/dev/null || echo '')"

info() {
  printf "${BOLD}${GREY}>${NO_COLOR} $@\n"
}

warn() {
  printf "${YELLOW}! $@${NO_COLOR}\n"
}

error() {
  printf "${RED}x $@${NO_COLOR}\n" >&2
}

complete() {
  printf "${GREEN}✓${NO_COLOR} $@\n"
}

fetch() {
  local command
  if hash curl 2>/dev/null; then
    set +e
    command="curl -L --silent --fail $1"
    curl -L --silent --fail "$1"
    rc=$?
    set -e
  else
    if hash wget 2>/dev/null; then
      set +e
      command="wget -O- -q $1"
      wget -O- -q "$1"
      rc=$?
      set -e
    else
      error "No HTTP download program (curl, wget) found…"
      exit 1
    fi
  fi

  if [ $rc -ne 0 ]; then
    error "Command failed (exit code $rc): ${BLUE}${command}${NO_COLOR}"
    exit $rc
  fi
}

resolve_version() {
  if [ -z "${UNSAFE-}" ]; then
    local tag="$1"

    if [ "${tag}" = "latest" ]; then
      version=$(
        fetch "https://downloads.akka.io/latest/akka_version.txt" | \
        tr -d '[:space:]')
    else
      local exists=$(
        fetch "https://downloads.akka.io/" | \
        grep -o "<Key>${tag}/akka</Key>" | \
        tr -d '[:space:]')

      if [ -z "${exists}" ]; then
        # error
        version=""
      else
        version="$tag"
      fi
    fi

    echo "${version}"
  else
    echo "$1"
  fi
}

# Currently known to support:
#   - win (Git Bash)
#   - darwin
#   - linux
detect_platform() {
  local platform="$(uname -s | tr '[:upper:]' '[:lower:]')"

  # mingw is Git-Bash
  if echo "${platform}" | grep -i mingw >/dev/null; then
    platform=win
  fi

  echo "${platform}"
}

# Currently known to support:
#   - x64 (x86_64)
detect_arch() {
  local arch="$(uname -m | tr '[:upper:]' '[:lower:]')"

  if echo "${arch}" | grep -i arm >/dev/null; then
    if [ "$(uname -s | tr '[:upper:]' '[:lower:]')" = "darwin" ]; then
      arch=arm64
    else
      error "Detected ARM64 for non-Apple Silicon chip"
      exit 1
    fi
  else
    if [ "${arch}" = "i386" ]; then
      error "Detected i386"
      exit 1
    elif [ "${arch}" = "x86_64" ]; then
      # Get the actual hardware brand string to double-check for Rosetta.
      # If the terminal is running via Rosetta 2 (Intel emulation), `uname -m`   will return x86_64.
      # To see the actual physical hardware regardless of the terminal's state use `sysctl`.
      BRAND=$(sysctl -n machdep.cpu.brand_string)
      if [[ "$BRAND" == *"Apple"* ]]; then
        arch=arm64
      else
        arch=amd64
      fi
    elif [ "${arch}" = "aarch64" ]; then
      arch=arm64
    fi

    # `uname -m` in some cases mis-reports 32-bit OS as 64-bit, so double check
    if [ "${arch}" = "amd64" ] && [ "$(getconf LONG_BIT)" -eq 32 ]; then
      error "Detected 32 bit"
      exit 1
    fi

  fi
  echo "${arch}"
}

confirm() {
  if [ -z "${FORCE-}" ]; then
    printf "${MAGENTA}?${NO_COLOR} $@ ${BOLD}[yN]${NO_COLOR} "
    set +e
    read yn < /dev/tty
    rc=$?
    set -e
    if [ $rc -ne 0 ]; then
      error "Error reading from prompt (please re-run with the \`--yes\` option)"
      exit 1
    fi
    if [ "$yn" != "y" ] && [ "$yn" != "yes" ]; then
      error "Aborting"
      exit 1
    fi
  fi
}

check_prefix() {
  local bin="$1"

  # https://stackoverflow.com/a/11655875
  local good=$( IFS=:
    for path in $PATH; do
      if [ "${path}" = "${bin}" ]; then
        echo 1
        break
      fi
    done
  )

  if [ ! -d "${bin}" ] ; then
    error "Installation directory ${bin} does not exist"
    exit 1
  fi

  if [ ! -w "${bin}" ] ; then
    error "Installation directory ${bin} is not writable by current user. Suggest trying:"
    info "curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix /tmp && \ "
    info "    sudo mv /tmp/akka /usr/local/bin/akka"
    exit 1
  fi

  if [ "${good}" != "1" ]; then
    warn "Installation directory ${bin} is not in your \$PATH"
  fi
}

# defaults
if [ -z "${VERSION-}" ]; then
  VERSION=latest
fi

if [ -z "${PLATFORM-}" ]; then
  PLATFORM="$(detect_platform)"
fi

if [ -z "${PREFIX-}" ]; then
  PREFIX=/usr/local/bin
fi

if [ -z "${ARCH-}" ]; then
  ARCH="$(detect_arch)"
fi

if [ -z "${BASE_URL-}" ]; then
  BASE_URL="https://downloads.akka.io/"
fi

# parse argv variables
while [ "$#" -gt 0 ]; do
  case "$1" in
    -v|--version) VERSION="$2"; shift 2;;
    -p|--platform) PLATFORM="$2"; shift 2;;
    -P|--prefix) PREFIX="$2"; shift 2;;
    -a|--arch) ARCH="$2"; shift 2;;
    -b|--base-url) BASE_URL="$2"; shift 2;;

    -V|--verbose) VERBOSE=1; shift 1;;
    -f|-y|--force|--yes) FORCE=1; shift 1;;
    -u|--unsafe) UNSAFE=1; shift 1;;

    -v=*|--version=*) VERSION="${1#*=}"; shift 1;;
    -p=*|--platform=*) PLATFORM="${1#*=}"; shift 1;;
    -P=*|--prefix=*) PREFIX="${1#*=}"; shift 1;;
    -a=*|--arch=*) ARCH="${1#*=}"; shift 1;;
    -b=*|--base-url=*) BASE_URL="${1#*=}"; shift 1;;
    -V=*|--verbose=*) VERBOSE="${1#*=}"; shift 1;;
    -f=*|-y=*|--force=*|--yes=*) FORCE="${1#*=}"; shift 1;;

    -*) error "Unknown option: $1"; exit 1;;
    *) VERSION="$1"; shift 1;;
  esac
done

RESOLVED="$(resolve_version "$VERSION")"
if [ -z "${RESOLVED}" ]; then
  error "Could not resolve Akka CLI version ${MAGENTA}${VERSION}${NO_COLOR}"
  exit 1
fi

if [ -z "${ARCH}" ]; then
  error "Unsupported architecture"
  exit 1
fi


PRETTY_VERSION="${GREEN}${RESOLVED}${NO_COLOR}"
if [ "$RESOLVED" != "v$(echo "$VERSION" | sed 's/^v//')" ]; then
  PRETTY_VERSION="$PRETTY_VERSION (resolved from ${CYAN}${VERSION}${NO_COLOR})"
fi
printf "  ${UNDERLINE}Configuration${NO_COLOR}\n"
info "${BOLD}Version${NO_COLOR}:  ${PRETTY_VERSION}"
info "${BOLD}Prefix${NO_COLOR}:   ${GREEN}${PREFIX}${NO_COLOR}"
info "${BOLD}Platform${NO_COLOR}: ${GREEN}${PLATFORM}${NO_COLOR}"
info "${BOLD}Arch${NO_COLOR}:     ${GREEN}${ARCH}${NO_COLOR}"

# non-empty VERBOSE enables verbose untarring
if [ ! -z "${VERBOSE-}" ]; then
  VERBOSE=v
  info "${BOLD}Verbose${NO_COLOR}: yes"
else
  VERBOSE=
fi

echo

EXT=tar.gz

URL="${BASE_URL}${RESOLVED}/akka_${PLATFORM}_${ARCH}_${RESOLVED}.tar.gz"
info "Tarball URL: ${UNDERLINE}${BLUE}${URL}${NO_COLOR}"
check_prefix "${PREFIX}"
confirm "Install Akka CLI ${GREEN}${RESOLVED}${NO_COLOR} to ${BOLD}${GREEN}${PREFIX}${NO_COLOR}?"

info "Installing Akka CLI, please wait…"

{
  rm -f "${PREFIX}/akka" || true

  fetch "${URL}" \
    | tar xzf${VERBOSE} - \
      -C "${PREFIX}"
} || {
  error "Cannot extract the archive to: ${PREFIX}. Suggest trying:"
  info "curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix /tmp && \ "
  info "    sudo mv /tmp/akka-cli /usr/local/bin/akka"
  exit 1
}

if [ "${PLATFORM}" = darwin ]; then
  ignored_failure=$(xattr -d com.apple.quarantine "${PREFIX}/akka" >/dev/null 2>/dev/null || true)
fi

complete "Done"
