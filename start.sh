#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# start.sh — start the rewards-service locally.
#
# Modes:
#   ./start.sh              # auto: docker if available, else mvn
#   ./start.sh docker       # build & run via Docker (foreground)
#   ./start.sh mvn          # run via Maven (./mvnw spring-boot:run)
#
# Env overrides:
#   PORT=8080
#   IMAGE=rewards-service:1.0.0
#   CONTAINER=rewards-service
# -----------------------------------------------------------------------------
set -euo pipefail

PORT="${PORT:-8080}"
IMAGE="${IMAGE:-rewards-service:1.0.0}"
CONTAINER="${CONTAINER:-rewards-service}"
MODE="${1:-auto}"

log() { printf '\033[1;34m[start]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[start]\033[0m %s\n' "$*" >&2; exit 1; }

run_docker() {
  command -v docker >/dev/null 2>&1 || die "docker is not installed."
  docker info >/dev/null 2>&1 || die "Docker daemon is not running. Start Docker Desktop and retry."

  log "Building image '${IMAGE}'..."
  docker build -t "${IMAGE}" .

  log "Removing any pre-existing container named '${CONTAINER}'..."
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true

  log "Starting '${CONTAINER}' on http://localhost:${PORT} (Ctrl+C to stop)..."
  exec docker run --rm --name "${CONTAINER}" -p "${PORT}:8080" "${IMAGE}"
}

run_mvn() {
  if [[ -x ./mvnw ]]; then
    MVN=./mvnw
  elif command -v mvn >/dev/null 2>&1; then
    MVN=mvn
  else
    die "Neither ./mvnw nor mvn is available."
  fi
  log "Starting via ${MVN} on http://localhost:${PORT} (Ctrl+C to stop)..."
  exec "${MVN}" spring-boot:run -Dspring-boot.run.arguments="--server.port=${PORT}"
}

case "${MODE}" in
  docker) run_docker ;;
  mvn|maven) run_mvn ;;
  auto)
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
      run_docker
    else
      log "Docker not available; falling back to Maven."
      run_mvn
    fi
    ;;
  *) die "Unknown mode '${MODE}'. Use: docker | mvn | auto" ;;
esac
