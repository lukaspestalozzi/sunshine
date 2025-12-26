# Android/Kotlin Build and Verification

## Quick Reference

| Task | Command |
|------|---------|
| **Quick verify (standalone)** | `./scripts/verify-local.sh --standalone` |
| **Full verify (Gradle, CI match)** | `./scripts/verify-local.sh --gradle` |
| **ktlintCheck** | `./scripts/run-with-proxy.sh ktlintCheck` |
| **detekt** | `./scripts/run-with-proxy.sh detekt` |
| **lintDebug** | `./scripts/run-with-proxy.sh lintDebug` |
| **Unit tests** | `./scripts/run-with-proxy.sh testDebugUnitTest` |
| **Build APK** | `./scripts/run-with-proxy.sh assembleDebug` |
| **All CI checks** | `./scripts/run-with-proxy.sh check` |

## Important: ktlint Version Difference

**Standalone ktlint 1.5.0 may miss rules that CI catches!**

| Tool | ktlint Version | Accuracy |
|------|---------------|----------|
| `verify-local.sh --standalone` | 1.5.0 | Fast but may miss rules |
| `run-with-proxy.sh ktlintCheck` | ~1.0-1.3 (Gradle plugin) | Matches CI exactly |

**Known difference:** Inline comments in argument lists:
```kotlin
// BAD - CI will fail (older ktlint catches this)
valueRange = 0f..86399f, // comment here

// GOOD - Comment on separate line
// comment here
valueRange = 0f..86399f,
```

**Always run `./scripts/run-with-proxy.sh ktlintCheck` before pushing!**

## Verification Workflow

1. **During development**: `./scripts/verify-local.sh --standalone` (fast)
2. **Before committing**: `./scripts/run-with-proxy.sh ktlintCheck detekt` (CI match)
3. **Before pushing**: `./scripts/run-with-proxy.sh check` (full CI simulation)

## CI Pipeline Steps

The CI runs these steps in order:
1. `ktlintCheck` - Code style
2. `detekt` - Static analysis
3. `lintDebug` - Android lint
4. `testDebugUnitTest` - Unit tests
5. `assembleDebug` - Build APK

Run locally with: `./scripts/run-with-proxy.sh ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug`

## Script Usage

### `./scripts/verify-local.sh`

```bash
./scripts/verify-local.sh              # Auto-detect method
./scripts/verify-local.sh --standalone # Fast, standalone tools
./scripts/verify-local.sh --gradle     # Slower, matches CI exactly
```

### `./scripts/run-with-proxy.sh`

```bash
./scripts/run-with-proxy.sh ktlintCheck
./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.SimpleSunCalculatorTest"
./scripts/run-with-proxy.sh check
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

- **Standalone**: Python 3, Java 17+, curl
- **Gradle**: Python 3, Java 17+, Android SDK (ANDROID_HOME)

## How Proxy Works

Java's `HttpURLConnection` doesn't send proxy auth for HTTPS CONNECT. The `auth-proxy.py`:
1. Reads credentials from `HTTP_PROXY`/`HTTPS_PROXY`
2. Listens on `127.0.0.1:3128`
3. Injects auth into CONNECT requests

This is transparent when using `run-with-proxy.sh`.
