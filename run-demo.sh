#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# run-demo.sh
#
# Builds the rewards-service Docker image, starts a container, waits for the
# service to become healthy, then exercises the REST API and saves each
# response into ./examples/ as both raw JSON and a human-readable Markdown
# walkthrough.
#
# Usage:
#   ./run-demo.sh                # build + run + demo
#   KEEP_RUNNING=1 ./run-demo.sh # leave the container up after the demo
# -----------------------------------------------------------------------------
set -euo pipefail

IMAGE="${IMAGE:-rewards-service:1.0.0}"
CONTAINER="${CONTAINER:-rewards-service-demo}"
PORT="${PORT:-8080}"
BASE_URL="http://localhost:${PORT}"
EXAMPLES_DIR="${EXAMPLES_DIR:-examples}"
HEALTH_TIMEOUT_SECS="${HEALTH_TIMEOUT_SECS:-60}"

# ---- helpers ----------------------------------------------------------------
log()  { printf '\033[1;34m[demo]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[demo]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[demo]\033[0m %s\n' "$*" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not installed."
}

require docker
require curl

PRETTY="cat"
if command -v jq >/dev/null 2>&1; then
  PRETTY="jq ."
else
  warn "jq not found; JSON output will not be pretty-printed."
fi

cleanup() {
  if [[ "${KEEP_RUNNING:-0}" == "1" ]]; then
    log "KEEP_RUNNING=1 set — leaving container '${CONTAINER}' up on port ${PORT}."
    return
  fi
  log "Stopping container '${CONTAINER}'..."
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---- build & run ------------------------------------------------------------
log "Building image '${IMAGE}'..."
docker build -t "${IMAGE}" . >/dev/null

log "Removing any pre-existing container named '${CONTAINER}'..."
docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true

log "Starting container '${CONTAINER}' on port ${PORT}..."
docker run -d --name "${CONTAINER}" -p "${PORT}:8080" "${IMAGE}" >/dev/null

# ---- wait for health --------------------------------------------------------
log "Waiting up to ${HEALTH_TIMEOUT_SECS}s for ${BASE_URL}/actuator/health ..."
deadline=$(( $(date +%s) + HEALTH_TIMEOUT_SECS ))
until curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; do
  if (( $(date +%s) >= deadline )); then
    docker logs "${CONTAINER}" || true
    die "Service did not become healthy within ${HEALTH_TIMEOUT_SECS}s."
  fi
  sleep 1
done
log "Service is up."

# ---- run the demo calls -----------------------------------------------------
mkdir -p "${EXAMPLES_DIR}"
log "Writing example outputs into ./${EXAMPLES_DIR}/"

# Demo window: last 3 full months from today (matches default behaviour).
END_DATE="$(date +%F)"
START_DATE="$(date -v -3m +%F 2>/dev/null || date -d '3 months ago' +%F)"

call() {
  local name="$1" url="$2"
  local out="${EXAMPLES_DIR}/${name}.json"
  log "GET ${url}"
  local http_code
  # -f omitted on purpose so we capture 4xx/5xx response bodies too.
  http_code=$(curl -sS -o "${out}.raw" -w '%{http_code}' "${url}" || echo "000")
  echo "  HTTP ${http_code}"
  if [[ -s "${out}.raw" ]]; then
    ${PRETTY} < "${out}.raw" > "${out}" 2>/dev/null || cp "${out}.raw" "${out}"
  else
    echo "{\"httpStatus\":${http_code},\"note\":\"empty body\"}" > "${out}"
  fi
  rm -f "${out}.raw"
  echo "  -> ${out}"
}

call "01-all-customers-default-window"   "${BASE_URL}/api/v1/rewards"
call "02-all-customers-explicit-window"  "${BASE_URL}/api/v1/rewards?start=${START_DATE}&end=${END_DATE}"
call "03-customer-1-default-window"      "${BASE_URL}/api/v1/rewards/1"
call "04-customer-1-explicit-window"     "${BASE_URL}/api/v1/rewards/1?start=${START_DATE}&end=${END_DATE}"
call "05-customer-7-explicit-window"     "${BASE_URL}/api/v1/rewards/7?start=${START_DATE}&end=${END_DATE}"
call "06-unknown-customer-404"           "${BASE_URL}/api/v1/rewards/9999"
call "07-actuator-health"                "${BASE_URL}/actuator/health"

# ---- summary markdown -------------------------------------------------------
SUMMARY="${EXAMPLES_DIR}/README.md"
{
  echo "# Rewards Service — Example Run"
  echo ""
  echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo ""
  echo "Base URL: \`${BASE_URL}\`"
  echo "Demo window: \`${START_DATE}\` → \`${END_DATE}\`"
  echo ""
  for f in "${EXAMPLES_DIR}"/*.json; do
    [[ "$f" == "${SUMMARY}" ]] && continue
    name="$(basename "$f" .json)"
    echo "## ${name}"
    echo ""
    echo '```json'
    head -c 4000 "$f"
    echo ""
    echo '```'
    echo ""
  done
} > "${SUMMARY}"

log "Demo complete. See ./${EXAMPLES_DIR}/README.md for a summary."
