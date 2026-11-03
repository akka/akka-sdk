#!/usr/bin/env bash
# Publication setup and catalog contract: release-metadata/README.md
set -euo pipefail
cd "$(dirname "$0")/../.."

catalog=release-metadata/versions.json
node .github/scripts/update-release-metadata.mjs --validate "$catalog"

# The helper uses rsync temporary files and rename, without deleting other content.
bash docs/bin/rsync-docs.sh "$catalog" 'akkarepo@gustav.akka.io:www/versions.json'
