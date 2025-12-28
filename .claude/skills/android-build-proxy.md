# Android/Kotlin Build and Verification

## Quick Reference

| Task | Command |
|------|---------|
| **Full CI simulation** | `./scripts/verify-local.sh` |
| **Quick check (ktlint + detekt)** | `./scripts/verify-local.sh --quick` |
| **Standalone (no Android SDK)** | `./scripts/verify-local.sh --standalone` |
| **Individual Gradle task** | `./scripts/run-with-proxy.sh <task>` |

## Environment Setup

### Android SDK Installation (if not present)

When `ANDROID_HOME` is not set or the SDK is missing, set up the Android SDK:

```bash
# 1. Create SDK directory
mkdir -p ~/android-sdk

# 2. Download Android command-line tools
curl -L -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d ~/android-sdk
mv ~/android-sdk/cmdline-tools ~/android-sdk/cmdline-tools-tmp
mkdir -p ~/android-sdk/cmdline-tools/latest
mv ~/android-sdk/cmdline-tools-tmp/* ~/android-sdk/cmdline-tools/latest/
rm -rf ~/android-sdk/cmdline-tools-tmp

# 3. Set environment variables
export ANDROID_HOME=~/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# 4. Start auth proxy for SDK downloads (handles proxy authentication)
python3 scripts/auth-proxy.py &
PROXY_PID=$!
sleep 2

# 5. Accept licenses and install required components
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 \
  --licenses

$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 \
  "platforms;android-35" "build-tools;35.0.0"

# 6. Stop the auth proxy
kill $PROXY_PID

# 7. Create local.properties for Gradle
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### Verifying SDK Setup

```bash
# Check ANDROID_HOME is set
echo $ANDROID_HOME

# Check local.properties exists
cat local.properties

# Verify SDK components installed
ls $ANDROID_HOME/platforms/
ls $ANDROID_HOME/build-tools/
```

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

**⚠️ CRITICAL: Always run `./scripts/verify-local.sh` before pushing!** Never push without local verification. If CI fails, you must fix it locally before pushing again.

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
| Full/Quick | `ANDROID_HOME` set or `local.properties` with `sdk.dir`, Java 17+ |
| Standalone | Java 17+, curl |

## How Proxy Works

Java's `HttpURLConnection` doesn't send proxy auth for HTTPS CONNECT. The `auth-proxy.py`:
1. Reads credentials from `HTTP_PROXY`/`HTTPS_PROXY`
2. Listens on `127.0.0.1:3128`
3. Injects auth into CONNECT requests

This is transparent when using `run-with-proxy.sh` or `verify-local.sh`.

## Troubleshooting

### "SDK location not found"
Create `local.properties` in project root:
```
sdk.dir=/path/to/your/android-sdk
```

### Proxy authentication errors during SDK download
Use the auth proxy:
```bash
python3 scripts/auth-proxy.py &
sdkmanager --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 "platforms;android-35"
```

### Gradle daemon issues
Kill stale daemons:
```bash
./gradlew --stop
```
