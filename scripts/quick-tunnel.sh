#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1
    pwd -P
)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
BACKEND_URL="${PI_MONITOR_BACKEND_URL:-http://127.0.0.1:8080}"

fail() {
    printf '[pi-monitor] ERROR: %s\n' "$*" >&2
    exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker is not installed or is not on PATH."
command -v curl >/dev/null 2>&1 || fail "curl is required."

if ! curl --fail --silent --show-error \
    "${BACKEND_URL}/api/sensors" >/dev/null; then
    printf '[pi-monitor] The backend is not reachable at %s.\n' "${BACKEND_URL}" >&2
    printf '[pi-monitor] Start it with: %s/scripts/pi-compose.sh up -d --build\n' \
        "${PROJECT_ROOT}" >&2
    exit 1
fi

printf '[pi-monitor] Backend check passed: %s\n' "${BACKEND_URL}"
printf '[pi-monitor] Starting a temporary Cloudflare Quick Tunnel...\n'
printf '[pi-monitor] Copy the generated https://*.trycloudflare.com hostname.\n'
printf '[pi-monitor] Stop the tunnel with Ctrl+C. The hostname will then expire.\n'

exec docker run --rm --network host \
    cloudflare/cloudflared:latest \
    tunnel --url "${BACKEND_URL}"
