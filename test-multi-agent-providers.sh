#!/usr/bin/env bash
set -euo pipefail

# Test the multi-agent sample with all three model providers.
# Requires OPENAI_API_KEY, GOOGLE_AI_GEMINI_API_KEY, and ANTHROPIC_API_KEY set.
# WEATHER_API_KEY is optional (falls back to FakeWeatherService).

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SAMPLE_DIR="${SCRIPT_DIR}/samples/multi-agent"
PORT=9000
BASE_URL="http://localhost:${PORT}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
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

echo -e "${BOLD}Compiling...${RESET}"
mvn -q compile

providers=("openai" "googleai-gemini" "anthropic")
failures=()

wait_for_ready() {
  local attempts=0
  # use a simple POST to /preferences as a health check (lightweight, no LLM call)
  while ! curl -sf -o /dev/null "${BASE_URL}/preferences/healthcheck" \
      -X POST -H 'Content-Type: application/json' \
      -d '{"preference":"test"}' 2>/dev/null; do
    attempts=$((attempts + 1))
    if [[ $attempts -ge 60 ]]; then
      echo -e "${RED}Service did not become ready in time${RESET}"
      return 1
    fi
    sleep 2
  done
}

poll_answer() {
  local url="$1"
  local attempts=0
  local max_attempts=60
  while true; do
    attempts=$((attempts + 1))
    if [[ $attempts -ge $max_attempts ]]; then
      echo ""
      return 1
    fi
    local http_code body
    http_code=$(curl -sf -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
    if [[ "$http_code" == "200" ]]; then
      body=$(curl -sf "$url" 2>/dev/null)
      if [[ -n "$body" ]]; then
        echo "$body"
        return 0
      fi
    fi
    sleep 3
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
  echo "Service ready."

  user_id="test-${provider}"
  session_id="session-${provider}-$$"

  # --- start the workflow ---
  echo "Starting workflow..."
  start_code=$(curl -sf -o /dev/null -w '%{http_code}' -X POST \
    "${BASE_URL}/activities/${user_id}/${session_id}" \
    -H 'Content-Type: application/json' \
    -d '{"message":"What outdoor activities can I do in Amsterdam today considering the weather?"}')

  if [[ "$start_code" != "201" ]]; then
    echo -e "${RED}FAIL${RESET} - ${provider}: workflow start returned HTTP ${start_code} (expected 201)"
    failures+=("$provider")
    stop_service
    continue
  fi
  echo "Workflow started (HTTP 201). Polling for answer..."

  # --- poll for the answer ---
  answer=$(poll_answer "${BASE_URL}/activities/${user_id}/${session_id}")

  if [[ -z "$answer" ]]; then
    echo -e "${RED}FAIL${RESET} - ${provider}: no answer after polling"
    failures+=("$provider")
    stop_service
    continue
  fi

  if echo "$answer" | grep -qi "^ERROR"; then
    echo -e "${RED}FAIL${RESET} - ${provider}: agent returned error: ${answer:0:200}"
    failures+=("$provider")
    stop_service
    continue
  fi

  echo -e "${YELLOW}Answer:${RESET} ${answer:0:200}..."
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
