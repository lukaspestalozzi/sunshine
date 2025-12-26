#!/bin/bash
# Local verification script for environments with proxy limitations
# Uses standalone tools that work with curl-based downloads

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="$PROJECT_DIR/.local-tools"

log_info() { echo "[INFO] $1"; }
log_warn() { echo "[WARN] $1"; }
log_error() { echo "[ERROR] $1"; }

mkdir -p "$TOOLS_DIR"

# Download ktlint if not present
setup_ktlint() {
    local ktlint_version="1.5.0"
    local ktlint_path="$TOOLS_DIR/ktlint"

    if [ ! -x "$ktlint_path" ]; then
        log_info "Downloading ktlint $ktlint_version..."
        curl -sSL "https://github.com/pinterest/ktlint/releases/download/${ktlint_version}/ktlint" -o "$ktlint_path"
        chmod +x "$ktlint_path"
    fi
    echo "$ktlint_path"
}

# Download detekt if not present
setup_detekt() {
    local detekt_version="1.23.7"
    local detekt_path="$TOOLS_DIR/detekt-cli.jar"

    if [ ! -f "$detekt_path" ]; then
        log_info "Downloading detekt $detekt_version..."
        curl -sSL "https://github.com/detekt/detekt/releases/download/v${detekt_version}/detekt-cli-${detekt_version}-all.jar" -o "$detekt_path"
    fi
    echo "$detekt_path"
}

# Run ktlint
run_ktlint() {
    log_info "Running ktlint..."
    local ktlint
    ktlint=$(setup_ktlint)

    cd "$PROJECT_DIR"
    if "$ktlint" "**/*.kt" "**/*.kts" 2>&1; then
        log_info "ktlint: PASSED"
        return 0
    else
        log_error "ktlint: FAILED"
        return 1
    fi
}

# Run detekt
run_detekt() {
    log_info "Running detekt..."
    local detekt
    detekt=$(setup_detekt)
    local config="$PROJECT_DIR/app/config/detekt/detekt.yml"

    cd "$PROJECT_DIR"
    if [ -f "$config" ]; then
        if java -jar "$detekt" --input app/src --config "$config" 2>&1; then
            log_info "detekt: PASSED"
            return 0
        else
            log_error "detekt: FAILED"
            return 1
        fi
    else
        log_warn "detekt config not found, running with defaults..."
        if java -jar "$detekt" --input app/src --build-upon-default-config 2>&1; then
            log_info "detekt: PASSED"
            return 0
        else
            log_error "detekt: FAILED"
            return 1
        fi
    fi
}

# Main
main() {
    log_info "=== Local Verification ==="
    log_info "Project: $PROJECT_DIR"
    echo

    local failed=0

    run_ktlint || failed=1
    echo

    run_detekt || failed=1
    echo

    if [ $failed -eq 0 ]; then
        log_info "=== All checks PASSED ==="
    else
        log_error "=== Some checks FAILED ==="
        exit 1
    fi
}

main "$@"
