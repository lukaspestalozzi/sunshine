#!/bin/bash
# Run Gradle commands through local auth proxy
# Solves Java's proxy authentication issues with HTTPS CONNECT tunneling
#
# Usage:
#   ./scripts/run-with-proxy.sh assembleDebug
#   ./scripts/run-with-proxy.sh test
#   ./scripts/run-with-proxy.sh ktlintCheck detekt

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

PROXY_PORT=3128
PROXY_PID=""

log_info() { echo "[INFO] $1"; }
log_error() { echo "[ERROR] $1"; }

cleanup() {
    if [ -n "$PROXY_PID" ]; then
        log_info "Stopping auth proxy (PID: $PROXY_PID)..."
        kill "$PROXY_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Start the auth proxy
start_proxy() {
    if ! command -v python3 &>/dev/null; then
        log_error "Python3 is required but not installed"
        exit 1
    fi

    if [ -z "$HTTP_PROXY" ] && [ -z "$HTTPS_PROXY" ]; then
        log_info "No proxy environment detected, running Gradle directly..."
        return 1
    fi

    log_info "Starting local auth proxy on port $PROXY_PORT..."
    python3 "$SCRIPT_DIR/auth-proxy.py" "$PROXY_PORT" &
    PROXY_PID=$!
    sleep 2

    # Verify proxy is running
    if ! kill -0 "$PROXY_PID" 2>/dev/null; then
        log_error "Failed to start auth proxy"
        exit 1
    fi

    return 0
}

# Run Gradle with proxy settings
run_gradle() {
    local proxy_args=""

    if [ -n "$PROXY_PID" ]; then
        proxy_args="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=$PROXY_PORT"
        # Unset environment proxy vars so Gradle uses our local proxy
        unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy
    fi

    log_info "Running: ./gradlew $proxy_args $*"
    cd "$PROJECT_DIR"
    ./gradlew $proxy_args "$@"
}

# Main
main() {
    if [ $# -eq 0 ]; then
        echo "Usage: $0 <gradle-tasks>"
        echo "Examples:"
        echo "  $0 assembleDebug"
        echo "  $0 test"
        echo "  $0 ktlintCheck detekt"
        exit 1
    fi

    # Stop any existing Gradle daemons that might have wrong proxy settings
    ./gradlew --stop 2>/dev/null || true

    if start_proxy; then
        run_gradle "$@"
    else
        # No proxy needed, run directly
        cd "$PROJECT_DIR"
        ./gradlew "$@"
    fi
}

main "$@"
