#!/bin/bash
# Local verification script
# Tries Gradle (matches CI exactly) first, falls back to standalone tools
#
# Usage:
#   ./scripts/verify-local.sh              # Auto-detect best method
#   ./scripts/verify-local.sh --gradle     # Force Gradle (via proxy)
#   ./scripts/verify-local.sh --standalone # Force standalone tools

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="$PROJECT_DIR/.local-tools"

log_info() { echo "[INFO] $1"; }
log_warn() { echo "[WARN] $1"; }
log_error() { echo "[ERROR] $1"; }

mkdir -p "$TOOLS_DIR"

# Try to run Gradle ktlint via proxy (matches CI exactly)
run_gradle_ktlint() {
    log_info "Running ktlintCheck via Gradle (matches CI)..."
    if "$SCRIPT_DIR/run-with-proxy.sh" ktlintCheck 2>&1; then
        log_info "ktlintCheck: PASSED"
        return 0
    else
        log_error "ktlintCheck: FAILED"
        return 1
    fi
}

# Try to run Gradle detekt via proxy
run_gradle_detekt() {
    log_info "Running detekt via Gradle (matches CI)..."
    if "$SCRIPT_DIR/run-with-proxy.sh" detekt 2>&1; then
        log_info "detekt: PASSED"
        return 0
    else
        log_error "detekt: FAILED"
        return 1
    fi
}

# Download ktlint if not present (fallback)
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

# Download detekt if not present (fallback)
setup_detekt() {
    local detekt_version="1.23.7"
    local detekt_path="$TOOLS_DIR/detekt-cli.jar"

    if [ ! -f "$detekt_path" ]; then
        log_info "Downloading detekt $detekt_version..."
        curl -sSL "https://github.com/detekt/detekt/releases/download/v${detekt_version}/detekt-cli-${detekt_version}-all.jar" -o "$detekt_path"
    fi
    echo "$detekt_path"
}

# Run standalone ktlint (fallback - may differ from CI)
run_standalone_ktlint() {
    log_warn "Running standalone ktlint (may differ from CI version)..."
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

# Run standalone detekt (fallback)
run_standalone_detekt() {
    log_info "Running standalone detekt..."
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

# Check if Gradle can work (proxy available and Android SDK present)
can_use_gradle() {
    # Check if ANDROID_HOME is set
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        return 1
    fi
    # Check if proxy script exists
    if [ ! -x "$SCRIPT_DIR/run-with-proxy.sh" ]; then
        return 1
    fi
    return 0
}

# Main
main() {
    local mode="auto"

    case "${1:-}" in
        --gradle) mode="gradle" ;;
        --standalone) mode="standalone" ;;
        --help|-h)
            echo "Usage: $0 [--gradle|--standalone]"
            echo ""
            echo "Options:"
            echo "  --gradle      Force Gradle verification (matches CI exactly)"
            echo "  --standalone  Force standalone tools (faster, may differ from CI)"
            echo "  (no option)   Auto-detect: try Gradle, fall back to standalone"
            exit 0
            ;;
    esac

    log_info "=== Local Verification ==="
    log_info "Project: $PROJECT_DIR"
    log_info "Mode: $mode"
    echo

    local failed=0
    local use_gradle=false

    if [ "$mode" = "gradle" ]; then
        use_gradle=true
    elif [ "$mode" = "auto" ] && can_use_gradle; then
        log_info "Android SDK detected, using Gradle for exact CI match"
        use_gradle=true
    else
        if [ "$mode" = "auto" ]; then
            log_warn "Android SDK not found, using standalone tools (may differ from CI)"
        fi
    fi

    if $use_gradle; then
        run_gradle_ktlint || failed=1
        echo
        run_gradle_detekt || failed=1
    else
        run_standalone_ktlint || failed=1
        echo
        run_standalone_detekt || failed=1
    fi
    echo

    if [ $failed -eq 0 ]; then
        log_info "=== All checks PASSED ==="
    else
        log_error "=== Some checks FAILED ==="
        exit 1
    fi
}

main "$@"
