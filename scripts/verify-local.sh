#!/bin/bash
# Local verification script - Matches CI Pipeline exactly
#
# CI Pipeline steps (in order):
#   1. ktlintCheck    - Code style
#   2. detekt         - Static analysis
#   3. lintDebug      - Android lint
#   4. testDebugUnitTest - Unit tests
#   5. assembleDebug  - Build APK
#
# Usage:
#   ./scripts/verify-local.sh              # Full CI simulation (all 5 steps)
#   ./scripts/verify-local.sh --quick      # Quick check (ktlint + detekt only)
#   ./scripts/verify-local.sh --standalone # Standalone tools (no Android SDK needed)

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="$PROJECT_DIR/.local-tools"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "[INFO] $1"; }
log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_step() { echo -e "\n${YELLOW}=== Step $1: $2 ===${NC}"; }

mkdir -p "$TOOLS_DIR"

# CI Pipeline steps
CI_STEPS=("ktlintCheck" "detekt" "lintDebug" "testDebugUnitTest" "assembleDebug")
CI_DESCRIPTIONS=(
    "Code style (ktlint)"
    "Static analysis (detekt)"
    "Android lint"
    "Unit tests"
    "Build APK"
)

# Run a Gradle task via proxy
run_gradle_task() {
    local task="$1"
    if "$SCRIPT_DIR/run-with-proxy.sh" "$task" 2>&1; then
        return 0
    else
        return 1
    fi
}

# Download ktlint if not present (for standalone mode)
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

# Download detekt if not present (for standalone mode)
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
    log_warn "Using standalone ktlint 1.5.0 (may differ from CI's ~1.0-1.3)"
    local ktlint
    ktlint=$(setup_ktlint)

    cd "$PROJECT_DIR"
    if "$ktlint" "**/*.kt" "**/*.kts" 2>&1; then
        return 0
    else
        return 1
    fi
}

# Run standalone detekt (fallback)
run_standalone_detekt() {
    log_info "Using standalone detekt..."
    local detekt
    detekt=$(setup_detekt)
    local config="$PROJECT_DIR/app/config/detekt/detekt.yml"

    cd "$PROJECT_DIR"
    if [ -f "$config" ]; then
        if java -jar "$detekt" --input app/src --config "$config" 2>&1; then
            return 0
        else
            return 1
        fi
    else
        log_warn "detekt config not found, running with defaults..."
        if java -jar "$detekt" --input app/src --build-upon-default-config 2>&1; then
            return 0
        else
            return 1
        fi
    fi
}

# Check if Gradle can work (proxy script exists and Android SDK present)
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

# Run full CI pipeline via Gradle
run_full_ci() {
    local failed=0
    local passed=0
    local step_num=0

    for i in "${!CI_STEPS[@]}"; do
        step_num=$((i + 1))
        local task="${CI_STEPS[$i]}"
        local desc="${CI_DESCRIPTIONS[$i]}"

        log_step "$step_num/5" "$desc"

        if run_gradle_task "$task"; then
            log_pass "$task"
            ((passed++))
        else
            log_fail "$task"
            ((failed++))
            # Continue to show all failures, but mark as failed
        fi
    done

    echo
    echo "========================================"
    echo "Results: $passed passed, $failed failed"
    echo "========================================"

    if [ $failed -gt 0 ]; then
        return 1
    fi
    return 0
}

# Run quick checks (ktlint + detekt only)
run_quick_checks() {
    local use_gradle="$1"
    local failed=0

    log_step "1/2" "Code style (ktlint)"
    if $use_gradle; then
        if run_gradle_task "ktlintCheck"; then
            log_pass "ktlintCheck"
        else
            log_fail "ktlintCheck"
            ((failed++))
        fi
    else
        if run_standalone_ktlint; then
            log_pass "ktlint (standalone)"
        else
            log_fail "ktlint (standalone)"
            ((failed++))
        fi
    fi

    log_step "2/2" "Static analysis (detekt)"
    if $use_gradle; then
        if run_gradle_task "detekt"; then
            log_pass "detekt"
        else
            log_fail "detekt"
            ((failed++))
        fi
    else
        if run_standalone_detekt; then
            log_pass "detekt (standalone)"
        else
            log_fail "detekt (standalone)"
            ((failed++))
        fi
    fi

    echo
    if [ $failed -gt 0 ]; then
        return 1
    fi
    return 0
}

# Print usage
print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Runs local verification matching CI pipeline."
    echo ""
    echo "Options:"
    echo "  (no option)    Full CI simulation: all 5 steps via Gradle"
    echo "  --quick        Quick check: ktlint + detekt only (via Gradle)"
    echo "  --standalone   Standalone tools: ktlint + detekt without Android SDK"
    echo "  --help, -h     Show this help message"
    echo ""
    echo "CI Pipeline Steps:"
    echo "  1. ktlintCheck       - Code style"
    echo "  2. detekt            - Static analysis"
    echo "  3. lintDebug         - Android lint"
    echo "  4. testDebugUnitTest - Unit tests"
    echo "  5. assembleDebug     - Build APK"
    echo ""
    echo "Requirements:"
    echo "  Full/Quick:   ANDROID_HOME set, Java 17+"
    echo "  Standalone:   Java 17+, curl"
    echo ""
    echo "Examples:"
    echo "  $0                   # Run full CI (recommended before push)"
    echo "  $0 --quick           # Fast check during development"
    echo "  $0 --standalone      # When Android SDK not available"
}

# Main
main() {
    local mode="full"

    case "${1:-}" in
        --quick) mode="quick" ;;
        --standalone) mode="standalone" ;;
        --help|-h)
            print_usage
            exit 0
            ;;
        "")
            mode="full"
            ;;
        *)
            echo "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac

    echo "========================================"
    echo "  Local CI Verification"
    echo "========================================"
    echo "Project: $PROJECT_DIR"
    echo "Mode:    $mode"

    if [ "$mode" = "standalone" ]; then
        echo ""
        log_warn "Standalone mode: only ktlint + detekt"
        log_warn "ktlint version may differ from CI!"
        echo ""
        if ! run_quick_checks false; then
            log_fail "Some checks failed"
            exit 1
        fi
        log_pass "All standalone checks passed"
        exit 0
    fi

    # Check if we can use Gradle
    if ! can_use_gradle; then
        echo ""
        log_fail "Cannot run Gradle verification:"
        log_fail "  - ANDROID_HOME or ANDROID_SDK_ROOT must be set"
        log_fail "  - run-with-proxy.sh must exist"
        echo ""
        log_info "Options:"
        log_info "  1. Set ANDROID_HOME and retry"
        log_info "  2. Use --standalone for quick checks (may miss CI issues)"
        exit 1
    fi

    echo ""

    if [ "$mode" = "quick" ]; then
        log_info "Quick mode: ktlint + detekt only"
        echo ""
        if ! run_quick_checks true; then
            log_fail "Some checks failed"
            exit 1
        fi
        log_pass "Quick checks passed"
        log_warn "Note: lintDebug, tests, and build not verified"
    else
        log_info "Full CI simulation: all 5 steps"
        echo ""
        if ! run_full_ci; then
            log_fail "CI simulation failed"
            exit 1
        fi
        log_pass "Full CI simulation passed!"
        log_info "Safe to push - matches CI exactly"
    fi
}

main "$@"
