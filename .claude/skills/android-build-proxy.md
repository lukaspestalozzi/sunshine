# Local Development Guide

Complete guide for setting up, building, and testing the Sunshine Android project locally.

---

## Quick Reference

| Task | Command |
|------|---------|
| **Full CI simulation** | `./scripts/verify-local.sh` |
| **Quick check** | `./scripts/verify-local.sh --quick` |
| **Build APK** | `./scripts/run-with-proxy.sh assembleDebug` |
| **Run all tests** | `./scripts/run-with-proxy.sh testDebugUnitTest` |
| **Run specific test** | `./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.MyTest"` |
| **Fix code style** | `./scripts/run-with-proxy.sh ktlintFormat` |

---

## 1. Environment Setup

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 17+ |
| Android SDK | API 35 |
| Build Tools | 35.0.0 |

### Android SDK Installation

If `ANDROID_HOME` is not set or SDK is missing:

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

# 4. Start auth proxy (handles proxy authentication)
python3 scripts/auth-proxy.py &
PROXY_PID=$!
sleep 2

# 5. Accept licenses and install components
yes | sdkmanager --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 --licenses
sdkmanager --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 \
  "platforms;android-35" "build-tools;35.0.0"

# 6. Stop proxy and create local.properties
kill $PROXY_PID
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### Verify Setup

```bash
echo $ANDROID_HOME                    # Should show SDK path
cat local.properties                  # Should have sdk.dir=...
ls $ANDROID_HOME/platforms/           # Should show android-35
ls $ANDROID_HOME/build-tools/         # Should show 35.0.0
```

---

## 2. Building the Project

### Build Commands

```bash
# Debug build
./scripts/run-with-proxy.sh assembleDebug

# Release build
./scripts/run-with-proxy.sh assembleRelease

# Clean build
./scripts/run-with-proxy.sh clean assembleDebug
```

### Build Output

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

---

## 3. Testing

### Running Tests

```bash
# All unit tests
./scripts/run-with-proxy.sh testDebugUnitTest

# Specific test class
./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.SunCalculatorTest"

# Specific test method (use full method name with backticks escaped)
./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.SunCalculatorTest.sun is below horizon at midnight"
```

### Test Reports

- HTML: `app/build/reports/tests/testDebugUnitTest/index.html`
- XML: `app/build/test-results/testDebugUnitTest/`

### Test Patterns and Tips

#### MockK with Coroutines

```kotlin
// Use coEvery for suspend functions
coEvery { repository.getData() } returns Result.success(data)

// Use answers for dynamic responses
coEvery { repository.getElevations(any()) } answers {
    val points = firstArg<List<GeoPoint>>()
    Result.success(points.associateWith { 1000.0 })
}

// Verify suspend function calls
coVerify { repository.save(any()) }
coVerify(exactly = 0) { api.fetch(any()) }
```

#### Handling Nullable Returns

```kotlin
// BAD - assertEquals fails with nullable types
assertEquals(expected, result.getOrNull(), 0.1)

// GOOD - Use non-null assertion after checking success
assertTrue(result.isSuccess)
assertEquals(expected, result.getOrNull()!!, 0.1)
```

#### Test Method Length

Detekt enforces max 30 lines per method. Extract helpers:

```kotlin
// Extract entity creation to helper
private fun createTestEntity(lat: Double, lon: Double, elevation: Double) =
    ElevationEntity(
        gridLat = lat,
        gridLon = lon,
        latitude = lat,
        longitude = lon,
        elevation = elevation,
        source = "test",
        fetchedAt = System.currentTimeMillis(),
    )
```

#### Testing Thresholds and Calculations

When testing methods that use calculated thresholds:

```kotlin
// BAD - Uses arbitrary values that may not meet threshold
val bounds = BoundingBox(north = 47.0, south = 46.0, east = 9.0, west = 8.0)
coEvery { dao.countInBounds(any(), any(), any(), any()) } returns 100
// This may fail if 100 doesn't meet the calculated threshold!

// GOOD - Use bounds that result in predictable expected count
val bounds = BoundingBox(north = 46.001, south = 46.0, east = 8.001, west = 8.0)
// Calculate: ((0.001/resolution)+1)^2 expected points
// Return enough to meet threshold
coEvery { dao.countInBounds(any(), any(), any(), any()) } returns 16
```

---

## 4. Code Style

### ktlint

```bash
# Check style
./scripts/run-with-proxy.sh ktlintCheck

# Auto-fix style issues
./scripts/run-with-proxy.sh ktlintFormat
```

**Important**: Always use Gradle ktlint, not standalone. Standalone ktlint 1.5.0 may miss rules that CI catches.

#### Known ktlint Rules

```kotlin
// BAD - Inline comments in argument lists
valueRange = 0f..86400f, // seconds in day

// GOOD - Comment on separate line
// seconds in day
valueRange = 0f..86400f,
```

### detekt

```bash
# Run detekt
./scripts/run-with-proxy.sh detekt
```

#### Configured Exceptions (`app/config/detekt/detekt.yml`)

- `LongMethod`: Ignored for `@Composable` functions
- `MaxLineLength`: Set to 140

#### Suppressions (always add justification)

```kotlin
@Suppress("MagicNumber") // Astronomical constants from NOAA algorithm
@Suppress("LongMethod") // Complex calculation is cohesive
@Suppress("TooGenericExceptionCaught") // Calculator may throw various exceptions
```

---

## 5. CI Pipeline

### Pipeline Steps

| Step | Task | Description |
|------|------|-------------|
| 1 | `ktlintCheck` | Code style |
| 2 | `detekt` | Static analysis |
| 3 | `lintDebug` | Android lint |
| 4 | `testDebugUnitTest` | Unit tests |
| 5 | `assembleDebug` | Build APK |

### Verification Workflow

```
During development  → ./scripts/verify-local.sh --quick   (ktlint + detekt)
Before committing   → ./scripts/verify-local.sh --quick   (catch issues early)
Before pushing      → ./scripts/verify-local.sh           (full CI simulation)
```

**⚠️ CRITICAL**: Always run `./scripts/verify-local.sh` before pushing!

---

## 6. Proxy Configuration

### Why Proxy is Needed

Java's `HttpURLConnection` doesn't send proxy auth for HTTPS CONNECT requests. The `auth-proxy.py` script:

1. Reads credentials from `HTTP_PROXY`/`HTTPS_PROXY`
2. Listens on `127.0.0.1:3128`
3. Injects auth into CONNECT requests

### Usage

The proxy is automatically started by `run-with-proxy.sh` and `verify-local.sh`.

For manual SDK downloads:
```bash
python3 scripts/auth-proxy.py &
sdkmanager --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 "platforms;android-35"
```

---

## 7. Troubleshooting

### "SDK location not found"

Create `local.properties` in project root:
```properties
sdk.dir=/path/to/android-sdk
```

### Compilation errors with Room/KSP

```bash
# Clean and rebuild
./scripts/run-with-proxy.sh clean kspDebugKotlin
```

### Test assertion errors with assertEquals

Check for nullable types - use `!!` after verifying success:
```kotlin
assertTrue(result.isSuccess)
assertEquals(expected, result.getOrNull()!!, delta)
```

### Detekt LongMethod errors

Extract helper functions to reduce method length below 30 lines.

### ktlint errors not caught locally

Use Gradle ktlint (not standalone):
```bash
./scripts/run-with-proxy.sh ktlintCheck   # Matches CI exactly
```

### Gradle daemon issues

```bash
./gradlew --stop
```

### Proxy authentication errors

Ensure `HTTP_PROXY` and `HTTPS_PROXY` environment variables are set with credentials:
```bash
export HTTP_PROXY="http://user:pass@proxy:port"
export HTTPS_PROXY="http://user:pass@proxy:port"
```

---

## 8. Project Scripts

| Script | Purpose |
|--------|---------|
| `scripts/verify-local.sh` | Full CI simulation or quick checks |
| `scripts/run-with-proxy.sh` | Run Gradle commands through auth proxy |
| `scripts/auth-proxy.py` | Local proxy that handles Java HTTPS auth |
