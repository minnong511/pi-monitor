#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1
    pwd -P
)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
ENV_FILE="${PI_MONITOR_DOCKER_ENV_FILE:-${PROJECT_ROOT}/.env}"

if ! command -v docker >/dev/null 2>&1; then
    printf 'Docker is not installed or is not on PATH.\n' >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    printf 'The Docker Compose plugin is not available.\n' >&2
    exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
    printf 'Missing Docker environment file: %s\n' "${ENV_FILE}" >&2
    printf 'Create it from: %s/.env.example\n' "${PROJECT_ROOT}" >&2
    exit 1
fi

exec docker compose \
    --project-directory "${PROJECT_ROOT}" \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
