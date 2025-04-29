#!/usr/bin/env bash
# Run `make prod` first

# abort script if a command fails
set -e

find target/site -type d -name support -prune -o -type d -name reference -prune -o -type d -name _attachments -prune -o -type d -name services -prune -o -type d -name libraries -prune -o -name "404.html" -prune -o -name "*.adoc\[\].html" -prune -o \
  -type f -name "*.html" -exec sh -c 'npx -y d2m@latest -e -i {} -o {}.md ' \;

# replace numbered references in code snippets
find target/site -type f -name "*.html.md" -print0 | xargs -0 sed -i '' -E 's/\(([0-9]+)\)/\/\/ (\1)/g'

# update ask-akka-agent
#find target/site -name "index.html.md" -prune -o -type f -name "*.html.md" -exec cp {} samples/ask-akka-agent/src/main/resources/md-docs/ \;
