#!/bin/bash

VERSION=$1
if [ -z "$VERSION" ]; then
  echo "Usage: bin/update-license-and-pr.sh <version>"
  echo "Example: bin/update-license-and-pr.sh 3.2.0"
  exit 1
fi

CURRENT_YEAR=$(date +%Y)
CHANGE_DATE=$(date -v+3y +%Y-%m-%d)
BRANCH="release-license-$VERSION"

git checkout -b "$BRANCH" main

sed -i '' \
  -e "s/Licensed Work:        Akka SDK for Java v .*/Licensed Work:        Akka SDK for Java v $VERSION/" \
  -e "s/The Licensed Work is (c) [0-9]* Lightbend Inc./The Licensed Work is (c) $CURRENT_YEAR Lightbend Inc./" \
  -e "s/Change Date:          .*/Change Date:          $CHANGE_DATE/" \
  LICENSE

git add LICENSE
git commit -m "chore: License change date for $VERSION"
git push -u origin "$BRANCH"
gh pr create --base main --title "chore: License change date for $VERSION" --body ""
