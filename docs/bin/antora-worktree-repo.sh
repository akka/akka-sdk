#!/bin/bash

# Creates a scratch git repository for Antora to use as content source when the docs
# build runs from a linked git worktree. The Makefile bind mounts the docs and target
# directories of the worktree into this repository.
# Usage: ./antora-worktree-repo.sh <repo-dir>

set -e

REPO_DIR="$1"

if [ -z "$REPO_DIR" ]; then
    echo "Usage: $0 <repo-dir>"
    exit 1
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$BRANCH" = "HEAD" ]; then
    BRANCH="detached"
fi

ORIGIN="$(git config --get remote.origin.url || true)"

echo "Creating Antora content source repository in: $REPO_DIR"

rm -rf "$REPO_DIR"
git init -q -b "$BRANCH" "$REPO_DIR"
git -C "$REPO_DIR" -c commit.gpgsign=false commit -q --allow-empty -m "antora content source"

if [ -n "$ORIGIN" ]; then
    git -C "$REPO_DIR" remote add origin "$ORIGIN"
fi
