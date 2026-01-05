#!/bin/bash

# Configuration
REPO="akka/akka-sdk"
RUN_ID=$1

# Check if Run ID is provided
if [ -z "$RUN_ID" ]; then
    echo "Error: Please provide a Run ID."
    echo "Usage: ./collect_pr_links.sh 13446054522"
    exit 1
fi

echo "Scanning jobs for Run: https://github.com/$REPO/actions/runs/$RUN_ID"
echo "------------------------------------------------------------"

# 1. Fetch all Job IDs from the GitHub API
# Using 'gh' (GitHub CLI) is the most reliable way to handle authentication and pagination
JOB_IDS=$(gh api repos/$REPO/actions/runs/$RUN_ID/jobs --jq '.jobs[].id')

if [ -z "$JOB_IDS" ]; then
    echo "No jobs found. Ensure you are logged in ('gh auth login') and the Run ID is correct."
    exit 1
fi

# Temporary file to store results
TEMP_FILE="found_links.tmp"
touch $TEMP_FILE

# 2. Loop through each job to pull raw logs
for JOB_ID in $JOB_IDS; do
    JOB_NAME=$(gh api repos/$REPO/actions/jobs/$JOB_ID --jq '.name')
    echo "Checking Job: $JOB_NAME..."

    # 3. Stream raw logs and grep for the PR link pattern
    # Regex looks specifically for the akka/akka-sdk pull request format
    # echo "repos/$REPO/actions/jobs/$JOB_ID/logs"
    gh api repos/$REPO/actions/jobs/$JOB_ID/logs 2>/dev/null | \
    grep -oE "https://github.com/.*/pull/[0-9]+" >> $TEMP_FILE
done

# 4. Display unique results and cleanup
echo "------------------------------------------------------------"
if [ -s $TEMP_FILE ]; then
    # Get unique links
    UNIQUE_LINKS=$(sort -u "$TEMP_FILE")
    COUNT=$(echo "$UNIQUE_LINKS" | wc -l | xargs) # xargs trims whitespace

    echo "🔗 Unique Pull Request links found:"
    echo "$UNIQUE_LINKS"
    echo "------------------------------------------------------------"
    echo "📊 SUMMARY: Found $COUNT unique PR link(s)."
    # 4. Open links in the default browser
    echo "🚀 Opening links in browser..."
    for LINK in $UNIQUE_LINKS; do
        if command -v open > /dev/null; then
            open "$LINK" # macOS
        elif command -v xdg-open > /dev/null; then
            xdg-open "$LINK" # Linux
        elif command -v start > /dev/null; then
            start "$LINK" # Windows/WSL
        fi
    done
else
    echo "No PR links were found in the logs for this run."
fi

rm $TEMP_FILE
