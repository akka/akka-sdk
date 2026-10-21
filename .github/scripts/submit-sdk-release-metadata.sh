#!/usr/bin/env bash
# SDK release submission and catalog contract: release-metadata/README.md
set -euo pipefail

# Manual snapshot publications and qualified release tags do not update the catalog.
if [[ ${GITHUB_REF_TYPE:-} != tag || ! ${GITHUB_REF_NAME:-} =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Skipping metadata submission for a non-stable release tag'
  exit 0
fi
tag=$GITHUB_REF_NAME
version=${tag#v}
release=$(gh api "repos/${GITHUB_REPOSITORY:?}/releases/tags/$tag")
if jq -e '.draft == true or .prerelease == true' <<< "$release" >/dev/null; then
  echo 'Skipping draft or prerelease metadata'
  exit 0
fi
released_at=$(jq -er '.published_at' <<< "$release")

# publishM2 has installed this exact release's generated parent, including the
# compatibility values rewritten by the build rather than source placeholders.
pom=${1:-"$HOME/.m2/repository/io/akka/akka-javasdk-parent/$version/akka-javasdk-parent-$version.pom"}
required_runtime=$(sed -nE 's|.*<akka-runtime.version>([^<]+)</akka-runtime.version>.*|\1|p' "$pom")
protocol_version=$(sed -nE 's|.*<akka-protocol.version>([^<]+)</akka-protocol.version>.*|\1|p' "$pom")
[[ $required_runtime =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9]+([.-][A-Za-z0-9]+)*)?$ && ! $required_runtime =~ [Ss][Nn][Aa][Pp][Ss][Hh][Oo][Tt] ]] || { echo 'Invalid Runtime version in generated parent POM' >&2; exit 1; }
[[ $protocol_version =~ ^[0-9]+\.[0-9]+$ ]] || { echo 'Invalid protocol version in generated parent POM' >&2; exit 1; }

# Blank input preserves an existing URL or uses the catalog's public page default.
gh workflow run update-release-metadata.yml --repo "$GITHUB_REPOSITORY" --ref main \
  -f component=sdk -f version="$version" -f released_at="$released_at" \
  -f release_notes_url="${RELEASE_NOTES_URL:-}" \
  -f required_runtime="$required_runtime" -f protocol_version="$protocol_version"
