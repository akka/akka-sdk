#!/bin/bash

# Simple script to remove lines containing prettier-ignore comments
# Usage: ./remove-prettier-ignore.sh <directory>

TARGET_DIR="${1:-docs/src-managed}"

if [ ! -d "$TARGET_DIR" ]; then
    echo "Directory $TARGET_DIR does not exist. Skipping prettier-ignore removal."
    exit 0
fi

echo "Removing prettier-ignore lines from Java files in: $TARGET_DIR"

# Find all Java files and remove lines containing prettier-ignore
find "$TARGET_DIR" -type f -name "*.java" -exec sed -i '' '/prettier-ignore/d' {} \;

echo "Removed prettier-ignore lines from Java files in $TARGET_DIR"
