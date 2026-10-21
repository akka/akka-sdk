#!/usr/bin/env bash
# Workflow and pull request behavior: release-metadata/README.md
set -euo pipefail

catalog=release-metadata/versions.json
branch="automation/release-metadata-${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}"
git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
git switch -c "$branch"
node .github/scripts/update-release-metadata.mjs "$catalog"
git add -- "$catalog"
if git diff --cached --quiet; then
  echo 'Metadata unchanged; no pull request needed'
  exit 0
fi
message="chore: update $COMPONENT release metadata to $VERSION"
git commit -m "$message"
git push -u origin "$branch"
gh pr create --base main --head "$branch" --title "$message" --body ''
