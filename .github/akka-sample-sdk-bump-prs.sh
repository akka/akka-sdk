#!/bin/bash

# Finds the sample bump PRs created by a workflow run.
#
# Usage: ./.github/akka-sample-sdk-bump-prs.sh [--merge] [--dry-run] RUN_ID
#
#   (no flag)  Open each PR in the browser.
#   --merge    Approve and squash-merge each open PR where all checks pass.
#   --dry-run  With --merge: print the status of each PR and the planned action. Change nothing.

REPO="akka/akka-sdk"
MERGE=false
DRY_RUN=false
RUN_ID=""

for ARG in "$@"; do
    case "$ARG" in
        --merge) MERGE=true ;;
        --dry-run) DRY_RUN=true ;;
        -*) echo "Error: Unknown option $ARG"; exit 1 ;;
        *) RUN_ID=$ARG ;;
    esac
done

# Check if Run ID is provided
if [ -z "$RUN_ID" ]; then
    echo "Error: Please provide a Run ID."
    echo "Usage: ./.github/akka-sample-sdk-bump-prs.sh [--merge] [--dry-run] 13446054522"
    exit 1
fi

if [ "$DRY_RUN" = true ] && [ "$MERGE" = false ]; then
    echo "Error: --dry-run only applies together with --merge."
    exit 1
fi

echo "Scanning jobs for Run: https://github.com/$REPO/actions/runs/$RUN_ID"
echo "------------------------------------------------------------"

# 1. Fetch all Job IDs from the GitHub API
JOB_IDS=$(gh api --paginate repos/$REPO/actions/runs/$RUN_ID/jobs --jq '.jobs[].id')

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
    # The logs contain colour codes, gh refuses to print them without --allow-escape-sequences
    gh api --allow-escape-sequences repos/$REPO/actions/jobs/$JOB_ID/logs 2>/dev/null | \
    grep -oE "https://github.com/.*/pull/[0-9]+" >> $TEMP_FILE
done

# Prints "<state> <review decision> <checks>" for a PR, where checks is PASS, FAIL, PENDING or NONE
pr_status() {
    gh pr view "$1" --json state,reviewDecision,statusCheckRollup --jq '
        [.statusCheckRollup[] | (.conclusion // .state // "")] as $results
        | (if .reviewDecision == "" or .reviewDecision == null then "NONE" else .reviewDecision end) as $review
        | (if ($results | length) == 0 then "NONE"
           elif all($results[]; . == "SUCCESS" or . == "SKIPPED" or . == "NEUTRAL") then "PASS"
           elif any($results[]; . == "FAILURE" or . == "ERROR" or . == "CANCELLED" or . == "TIMED_OUT"
                    or . == "ACTION_REQUIRED" or . == "STARTUP_FAILURE") then "FAIL"
           else "PENDING" end) as $checks
        | "\(.state) \($review) \($checks)"'
}

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

    if [ "$MERGE" = true ]; then
        # 5. Approve and squash-merge each open PR with passing checks
        [ "$DRY_RUN" = true ] && echo "🧪 Dry run, no changes are made."
        MERGED=0
        SKIPPED=0
        FAILED=0
        for LINK in $UNIQUE_LINKS; do
            read -r STATE REVIEW CHECKS <<< "$(pr_status "$LINK")"
            echo "$LINK state=$STATE review=$REVIEW checks=$CHECKS"

            if [ "$STATE" != "OPEN" ]; then
                echo "   ⏭️  skip, PR is not open"
                SKIPPED=$((SKIPPED + 1))
                continue
            fi
            if [ "$CHECKS" != "PASS" ]; then
                echo "   ⏭️  skip, checks are not passing"
                SKIPPED=$((SKIPPED + 1))
                continue
            fi

            if [ "$DRY_RUN" = true ]; then
                [ "$REVIEW" != "APPROVED" ] && echo "   would approve"
                echo "   would squash-merge"
                MERGED=$((MERGED + 1))
                continue
            fi

            if [ "$REVIEW" != "APPROVED" ] && ! gh pr review "$LINK" --approve; then
                echo "   ❌ approve failed"
                FAILED=$((FAILED + 1))
                continue
            fi
            if gh pr merge "$LINK" --squash; then
                echo "   ✅ merged"
                MERGED=$((MERGED + 1))
            else
                echo "   ❌ merge failed"
                FAILED=$((FAILED + 1))
            fi
        done
        echo "------------------------------------------------------------"
        if [ "$DRY_RUN" = true ]; then
            echo "📊 Would merge: $MERGED, skip: $SKIPPED"
        else
            echo "📊 Merged: $MERGED, skipped: $SKIPPED, failed: $FAILED"
        fi
    else
        # 5. Open links in the default browser
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
    fi
else
    echo "No PR links were found in the logs for this run."
fi

rm $TEMP_FILE
