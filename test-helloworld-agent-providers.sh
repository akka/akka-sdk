#!/usr/bin/env bash
set -euo pipefail

# Test the helloworld-agent with all three model providers.
# Requires OPENAI_API_KEY, GOOGLE_AI_GEMINI_API_KEY, and ANTHROPIC_API_KEY set.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SAMPLE_DIR="${SCRIPT_DIR}/samples/helloworld-agent"
PORT=9000
URL="http://localhost:${PORT}/hello"

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
RESET='\033[0m'

# --- pre-flight checks ---
missing=()
[[ -z "${OPENAI_API_KEY:-}" ]]            && missing+=("OPENAI_API_KEY")
[[ -z "${GOOGLE_AI_GEMINI_API_KEY:-}" ]]  && missing+=("GOOGLE_AI_GEMINI_API_KEY")
[[ -z "${ANTHROPIC_API_KEY:-}" ]]         && missing+=("ANTHROPIC_API_KEY")

if [[ ${#missing[@]} -gt 0 ]]; then
  echo -e "${RED}Missing environment variables: ${missing[*]}${RESET}"
  exit 1
fi

cd "$SAMPLE_DIR"

# compile once up front
echo -e "${BOLD}Compiling...${RESET}"
mvn -q compile

providers=("openai" "googleai-gemini" "anthropic")
failures=()

wait_for_ready() {
  local attempts=0
  while ! curl -sf -o /dev/null "http://localhost:${PORT}/hello" -X POST \
      -H 'Content-Type: application/json' -d '{"user":"healthcheck","text":"ping"}' 2>/dev/null; do
    attempts=$((attempts + 1))
    if [[ $attempts -ge 60 ]]; then
      echo -e "${RED}Service did not become ready in time${RESET}"
      return 1
    fi
    sleep 2
  done
}

stop_service() {
  if [[ -n "${SVC_PID:-}" ]] && kill -0 "$SVC_PID" 2>/dev/null; then
    kill "$SVC_PID" 2>/dev/null || true
    wait "$SVC_PID" 2>/dev/null || true
    unset SVC_PID
  fi
}

trap stop_service EXIT

for provider in "${providers[@]}"; do
  echo ""
  echo -e "${BOLD}===== Testing provider: ${provider} =====${RESET}"

  # start the service with the chosen provider
  mvn -q exec:java \
    -Dakka.javasdk.agent.model-provider="$provider" &
  SVC_PID=$!

  echo "Waiting for service (pid $SVC_PID) to be ready..."
  if ! wait_for_ready; then
    echo -e "${RED}FAIL${RESET} - ${provider}: service did not start"
    failures+=("$provider")
    stop_service
    continue
  fi

  # --- first request ---
  echo "Sending first greeting..."
  resp1=$(curl -sf -X POST "$URL" \
    -H 'Content-Type: application/json' \
    -d '{"user":"test-'"$provider"'","text":"Hello!"}')

  if [[ -z "$resp1" ]]; then
    echo -e "${RED}FAIL${RESET} - ${provider}: empty response on first request"
    failures+=("$provider")
    stop_service
    continue
  fi
  echo "Response 1: ${resp1:0:120}..."

  # --- second request (same user → should use a different language) ---
  echo "Sending second greeting (session test)..."
  resp2=$(curl -sf -X POST "$URL" \
    -H 'Content-Type: application/json' \
    -d '{"user":"test-'"$provider"'","text":"Hello again!"}')

  if [[ -z "$resp2" ]]; then
    echo -e "${RED}FAIL${RESET} - ${provider}: empty response on second request"
    failures+=("$provider")
    stop_service
    continue
  fi
  echo "Response 2: ${resp2:0:120}..."

  echo -e "${GREEN}PASS${RESET} - ${provider}"

  stop_service
done

echo ""
echo -e "${BOLD}===== Summary =====${RESET}"
if [[ ${#failures[@]} -eq 0 ]]; then
  echo -e "${GREEN}All providers passed!${RESET}"
else
  echo -e "${RED}Failures: ${failures[*]}${RESET}"
  exit 1
fi
