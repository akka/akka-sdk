#!/bin/bash

# Simple script to remove lines containing prettier-ignore comments
# Usage: ./remove-prettier-ignore.sh <directory>

TARGET_DIR="${1:-docs/src-managed}"

if [ ! -d "$TARGET_DIR" ]; then
    echo "Directory $TARGET_DIR does not exist. Skipping prettier-ignore removal."
    exit 0
fi

echo "Removing prettier-ignore lines from Java files in: $TARGET_DIR"


OS_NAME="$(uname)"

# Detect OS for sed compatibility,
# Syntax (-i '') is specific to macOS (BSD sed).
# On Linux (GNU sed), this will cause an error, because it interprets '' as the name of a backup file.
if [[ "$OS_NAME" == "Darwin" ]]; then
    SED_INPLACE=("sed" "-i" "")
else
    SED_INPLACE=("sed" "-i")
fi

# Find all Java files and remove lines containing prettier-ignore
find "$TARGET_DIR" -type f -name "*.java" -exec "${SED_INPLACE[@]}" '/prettier-ignore/d' {} \;

echo "Removed prettier-ignore lines from Java files in $TARGET_DIR"
