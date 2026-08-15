#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1
    pwd -P
)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
VENV_DIR="${PROJECT_ROOT}/.venv"
REQUIREMENTS_FILE="${SCRIPT_DIR}/requirements.txt"
SENSOR_SCRIPT="${SCRIPT_DIR}/sensor_sender.py"

SERVICE_NAME="pi-monitor-sensor"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
ENV_DIR="/etc/pi-monitor"
ENV_FILE="${ENV_DIR}/sensor.env"

SERVICE_USER="$(id -un)"
SERVICE_GROUP="$(id -gn)"

API_URL="${PI_MONITOR_API_URL:-http://localhost:8080/api/sensors}"
SEND_INTERVAL="${PI_MONITOR_SEND_INTERVAL_SECONDS:-5}"
REQUEST_TIMEOUT="${PI_MONITOR_REQUEST_TIMEOUT_SECONDS:-5}"
TEMPERATURE_FILE="${PI_MONITOR_TEMPERATURE_FILE:-/sys/class/thermal/thermal_zone0/temp}"

log() {
    printf '[pi-monitor] %s\n' "$*"
}

fail() {
    printf '[pi-monitor] ERROR: %s\n' "$*" >&2
    exit 1
}

require_regular_user() {
    if [[ "${EUID}" -eq 0 ]]; then
        fail "Run this script as the Raspberry Pi login user, not with sudo."
    fi
}

validate_value() {
    local name="$1"
    local value="$2"

    if [[ -z "${value}" || "${value}" == *$'\n'* || "${value}" == *$'\r'* ]]; then
        fail "${name} must be a non-empty single-line value."
    fi
}

install_python() {
    command -v sudo >/dev/null 2>&1 || fail "sudo is required."
    command -v systemctl >/dev/null 2>&1 || fail "systemd is required."
    command -v apt-get >/dev/null 2>&1 || \
        fail "This installer supports Raspberry Pi OS/Debian with apt-get."

    log "Installing Python, venv, and pip packages..."
    sudo apt-get update
    sudo apt-get install -y python3 python3-venv python3-pip
}

install_python_dependencies() {
    log "Creating virtual environment at ${VENV_DIR}"
    python3 -m venv "${VENV_DIR}"

    "${VENV_DIR}/bin/python" -m pip install --upgrade pip setuptools wheel

    if ! "${VENV_DIR}/bin/python" -m pip install -r "${REQUIREMENTS_FILE}"; then
        log "A native dependency build is required; installing build tools..."
        sudo apt-get install -y build-essential python3-dev
        "${VENV_DIR}/bin/python" -m pip install -r "${REQUIREMENTS_FILE}"
    fi
}

write_environment_file() {
    validate_value "PI_MONITOR_API_URL" "${API_URL}"
    validate_value "PI_MONITOR_SEND_INTERVAL_SECONDS" "${SEND_INTERVAL}"
    validate_value "PI_MONITOR_REQUEST_TIMEOUT_SECONDS" "${REQUEST_TIMEOUT}"
    validate_value "PI_MONITOR_TEMPERATURE_FILE" "${TEMPERATURE_FILE}"

    sudo install -d -m 0755 "${ENV_DIR}"

    if [[ -f "${ENV_FILE}" && "${PI_MONITOR_OVERWRITE_ENV:-0}" != "1" ]]; then
        log "Keeping existing environment file: ${ENV_FILE}"
        log "Set PI_MONITOR_OVERWRITE_ENV=1 to replace it."
        return
    fi

    log "Writing ${ENV_FILE}"
    printf '%s\n' \
        "PI_MONITOR_API_URL=${API_URL}" \
        "PI_MONITOR_SEND_INTERVAL_SECONDS=${SEND_INTERVAL}" \
        "PI_MONITOR_REQUEST_TIMEOUT_SECONDS=${REQUEST_TIMEOUT}" \
        "PI_MONITOR_TEMPERATURE_FILE=${TEMPERATURE_FILE}" \
        | sudo tee "${ENV_FILE}" >/dev/null

    sudo chmod 0640 "${ENV_FILE}"
    sudo chown "root:${SERVICE_GROUP}" "${ENV_FILE}"
}

write_systemd_service() {
    log "Writing ${SERVICE_FILE}"

    printf '%s\n' \
        '[Unit]' \
        'Description=Pi Monitor Sensor Collector' \
        'Wants=network-online.target' \
        'After=network-online.target docker.service' \
        '' \
        '[Service]' \
        'Type=simple' \
        "User=${SERVICE_USER}" \
        "Group=${SERVICE_GROUP}" \
        "WorkingDirectory=\"${PROJECT_ROOT}\"" \
        "EnvironmentFile=-${ENV_FILE}" \
        'Environment=PYTHONUNBUFFERED=1' \
        "ExecStart=\"${VENV_DIR}/bin/python\" \"${SENSOR_SCRIPT}\"" \
        'Restart=always' \
        'RestartSec=5' \
        'TimeoutStopSec=15' \
        'NoNewPrivileges=true' \
        'PrivateTmp=true' \
        'UMask=0027' \
        '' \
        '[Install]' \
        'WantedBy=multi-user.target' \
        | sudo tee "${SERVICE_FILE}" >/dev/null

    sudo chmod 0644 "${SERVICE_FILE}"
}

enable_service() {
    log "Enabling and starting ${SERVICE_NAME}.service"
    sudo systemctl daemon-reload
    sudo systemctl enable --now "${SERVICE_NAME}.service"
}

main() {
    require_regular_user

    [[ -f "${REQUIREMENTS_FILE}" ]] || fail "Missing ${REQUIREMENTS_FILE}"
    [[ -f "${SENSOR_SCRIPT}" ]] || fail "Missing ${SENSOR_SCRIPT}"

    log "Detected project root: ${PROJECT_ROOT}"
    log "Service user: ${SERVICE_USER}"

    install_python
    install_python_dependencies
    write_environment_file
    write_systemd_service
    enable_service

    log "Installation complete."
    log "Status: sudo systemctl status ${SERVICE_NAME}"
    log "Logs:   journalctl -u ${SERVICE_NAME} -f"
}

main "$@"
