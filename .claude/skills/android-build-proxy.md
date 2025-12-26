# Android/Kotlin Build and Verification

## Quick Reference

| Task | Command |
|------|---------|
| **Full CI simulation** | `./scripts/verify-local.sh` |
| **Quick check (ktlint + detekt)** | `./scripts/verify-local.sh --quick` |
| **Standalone (no Android SDK)** | `./scripts/verify-local.sh --standalone` |
| **Individual Gradle task** | `./scripts/run-with-proxy.sh <task>` |

## CI Pipeline Steps

The CI runs these 5 steps in order. Local verification matches this exactly:

| Step | Gradle Task | Description |
|------|-------------|-------------|
| 1 | `ktlintCheck` | Code style |
| 2 | `detekt` | Static analysis |
| 3 | `lintDebug` | Android lint |
| 4 | `testDebugUnitTest` | Unit tests |
| 5 | `assembleDebug` | Build APK |

## Verification Workflow

```
During development    → ./scripts/verify-local.sh --quick     (fast: ktlint + detekt)
Before committing     → ./scripts/verify-local.sh --quick     (catch style issues early)
Before pushing        → ./scripts/verify-local.sh             (full CI: all 5 steps)
```

**Always run full verification before pushing!** This ensures CI will pass.

## Script Usage

### `./scripts/verify-local.sh`

```bash
./scripts/verify-local.sh              # Full CI simulation (all 5 steps)
./scripts/verify-local.sh --quick      # Quick check (ktlint + detekt only)
./scripts/verify-local.sh --standalone # Standalone tools (no Android SDK needed)
```

### `./scripts/run-with-proxy.sh`

Run individual Gradle tasks:

```bash
./scripts/run-with-proxy.sh ktlintCheck
./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.SimpleSunCalculatorTest"
./scripts/run-with-proxy.sh check
```

## Important: ktlint Version Difference

**Standalone ktlint 1.5.0 may miss rules that CI catches!**

| Tool | ktlint Version | Accuracy |
|------|---------------|----------|
| `verify-local.sh --standalone` | 1.5.0 | Fast but may miss rules |
| `verify-local.sh` or `--quick` | ~1.0-1.3 (Gradle plugin) | Matches CI exactly |

**Known difference:** Inline comments in argument lists:
```kotlin
// BAD - CI will fail (older ktlint catches this)
valueRange = 0f..86399f, // comment here

// GOOD - Comment on separate line
// comment here
valueRange = 0f..86399f,
```

## Auto-fixing Code Style

```bash
# Standalone ktlint (fast)
.local-tools/ktlint --format "**/*.kt" "**/*.kts"

# Gradle ktlint (matches CI)
./scripts/run-with-proxy.sh ktlintFormat
```

## Common detekt Suppressions

Already configured in `app/config/detekt/detekt.yml`:
- `LongMethod`: Ignored for `@Composable` functions
- `FunctionNaming`: Ignored for `@Composable` functions
- `MaxLineLength`: Set to 140

When suppressing rules, always add justification:
```kotlin
@Suppress("MagicNumber") // Astronomical constants from NOAA algorithm
@Suppress("LongMethod") // Complex calculation is cohesive
@Suppress("TooGenericExceptionCaught") // Calculator may throw various exceptions
@Suppress("UnusedParameter") // Will be used when feature is implemented
```

## Requirements

| Mode | Requirements |
|------|--------------|
| Full/Quick | `ANDROID_HOME` set, Java 17+ |
| Standalone | Java 17+, curl |

## How Proxy Works

Java's `HttpURLConnection` doesn't send proxy auth for HTTPS CONNECT. The `auth-proxy.py`:
1. Reads credentials from `HTTP_PROXY`/`HTTPS_PROXY`
2. Listens on `127.0.0.1:3128`
3. Injects auth into CONNECT requests

This is transparent when using `run-with-proxy.sh` or `verify-local.sh`.
