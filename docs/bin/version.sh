#!/usr/bin/env bash
#
# Version for docs, based on nearest git tag.
# This is always a tagged/released version, not a dynamic version.

# Fetch tags from remote to ensure we have the latest tags
# even when releasing from a branch
git fetch --tags --quiet 2> /dev/null || true

readonly prefix="v"
# Get the latest tag from all tags (not just those reachable from current commit)
# Exclude milestone releases (tags ending with -M followed by a number)
readonly tag=$(git tag -l "$prefix[0-9]*" 2> /dev/null | grep -v -- '-M[0-9]\+$' | sort -V | tail -n 1)
[ -n "$tag" ] && echo "${tag#$prefix}" || echo "0.0.0"
