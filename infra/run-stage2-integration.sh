#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.integration.yml"
PROJECT_NAME="github-graph-stage2-integration"

compose() {
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}

cleanup() {
  if [[ "${KEEP_STAGE2_TEST_STACK:-0}" != "1" ]]; then
    compose down --volumes --remove-orphans
  fi
}

wait_for_api() {
  local attempt
  for attempt in $(seq 1 45); do
    if compose exec -T api curl --fail --silent http://localhost:8080/actuator/health >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  compose logs api
  return 1
}

trap cleanup EXIT

compose down --volumes --remove-orphans
compose up --build --detach --wait

compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U github_graph -d github_graph_integration \
  -f /fixtures/stage2_seed.sql
compose exec -T neo4j \
  cypher-shell -u neo4j -p integrationpass -f /fixtures/stage2_graph.cypher

compose run --rm test-runner python /tests/stage2_integration.py before-restart

compose restart api
wait_for_api

compose run --rm test-runner python /tests/stage2_integration.py after-restart

echo "Stage 2 real-service integration test passed."
