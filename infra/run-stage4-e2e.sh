#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.e2e.yml"
PROJECT_NAME="github-graph-stage4-e2e"

compose() {
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}

cleanup() {
  if [[ "${KEEP_STAGE4_TEST_STACK:-0}" != "1" ]]; then
    compose down --volumes --remove-orphans
  fi
}

trap cleanup EXIT

if [[ "${STAGE4_INCLUDE_AI:-0}" == "1" ]]; then
  echo "Stage 4 AI verification will send bounded public-repository graph evidence to Gemini."
  echo "Continue only if this external data transfer is approved."
fi

compose down --volumes --remove-orphans

# These build targets execute the complete Python and Java test suites.
compose --profile quality build python-tests java-tests

# The web Dockerfile runs the production Next.js build.
compose build web api analysis
compose up --detach --wait postgres neo4j analysis api web

compose --profile test run --rm test-runner python /tests/stage4_e2e.py

if [[ "${RUN_STAGE2_INTEGRATION:-1}" == "1" ]]; then
  # Keep the clean persistence gate isolated without running two Neo4j stacks.
  compose down --volumes --remove-orphans
  bash "${SCRIPT_DIR}/run-stage2-integration.sh"
fi

echo "Stage 4 end-to-end and quality gates passed."
