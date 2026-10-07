#!/bin/bash
# Local verification: the same checks as CI (.github/workflows/ci.yml), in the same order.
#
#   1. openspec validate --all --strict   - OpenSpec specs and changes (CI job `specs`)
#   2. ktlintCheck                        - Code style
#   3. lintDebug                          - Android lint
#   4. :core:test :app:testDebugUnitTest  - Unit tests
#   5. assembleDebug                      - Debug APK
#
# Usage:
#   ./scripts/verify-local.sh          # all steps; run before every push
#   ./scripts/verify-local.sh --quick  # ktlint only
#
# Requirements: ANDROID_HOME (or ANDROID_SDK_ROOT) set, Java 17+, and the OpenSpec CLI
# (`npm install -g @fission-ai/openspec`; the version CI uses is in ci.yml).

set -u
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

STEPS=(
    "openspec validate --all --strict --no-interactive"
    "./gradlew ktlintCheck"
    "./gradlew lintDebug"
    "./gradlew :core:test :app:testDebugUnitTest"
    "./gradlew assembleDebug"
)

case "${1:-}" in
    "") ;;
    --quick) STEPS=("./gradlew ktlintCheck") ;;
    --help | -h)
        sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
    *)
        echo "Unknown option: $1 (see --help)"
        exit 1
        ;;
esac

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    echo -e "${RED}[FAIL]${NC} ANDROID_HOME or ANDROID_SDK_ROOT must be set"
    exit 1
fi
if [ "${#STEPS[@]}" -gt 1 ] && ! command -v openspec > /dev/null; then
    echo -e "${RED}[FAIL]${NC} openspec not found: npm install -g @fission-ai/openspec"
    exit 1
fi
export OPENSPEC_TELEMETRY=0

# Every step runs, so that one run shows all failures.
failed=0
for i in "${!STEPS[@]}"; do
    echo -e "\n${YELLOW}=== Step $((i + 1))/${#STEPS[@]}: ${STEPS[$i]} ===${NC}"
    if ${STEPS[$i]}; then
        echo -e "${GREEN}[PASS]${NC} ${STEPS[$i]}"
    else
        echo -e "${RED}[FAIL]${NC} ${STEPS[$i]}"
        failed=$((failed + 1))
    fi
done

echo
if [ "$failed" -gt 0 ]; then
    echo -e "${RED}[FAIL]${NC} $failed of ${#STEPS[@]} steps failed"
    exit 1
fi
echo -e "${GREEN}[PASS]${NC} All ${#STEPS[@]} steps passed"
