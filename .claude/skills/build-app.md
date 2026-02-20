# Build App

## When to use

When you need to build the Sunshine Android app APK in this environment (Claude Code remote container or similar CI/headless environment with proxy-authenticated internet access).

## Prerequisites

| Requirement | Details |
|-------------|---------|
| Java | 17+ (pre-installed in this environment) |
| Android SDK | API 35, Build-Tools 35.0.0 |
| Python | 3.x (for auth proxy script) |
| Internet | Via authenticated proxy (`HTTP_PROXY` / `HTTPS_PROXY` env vars) |

## Step 1: Install Android SDK (one-time)

If the SDK is not already installed at `~/android-sdk`:

```bash
# Download and install command-line tools
mkdir -p ~/android-sdk
curl -L -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d ~/android-sdk
mv ~/android-sdk/cmdline-tools ~/android-sdk/cmdline-tools-tmp
mkdir -p ~/android-sdk/cmdline-tools/latest
mv ~/android-sdk/cmdline-tools-tmp/* ~/android-sdk/cmdline-tools/latest/
rm -rf ~/android-sdk/cmdline-tools-tmp
```

Then install the required SDK components. Write this to a temporary script and run it (the proxy and sdkmanager commands need to run in the same shell):

```bash
#!/bin/bash
set -e
/usr/bin/python3 scripts/auth-proxy.py &
PROXY_PID=$!
sleep 3
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
  --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 \
  --licenses 2>&1 || true
~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
  --proxy=http --proxy_host=127.0.0.1 --proxy_port=3128 \
  "platforms;android-35" "build-tools;35.0.0" 2>&1
kill $PROXY_PID 2>/dev/null || true
```

Finally, create `local.properties` in the project root:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### Verify SDK installation

```bash
ls ~/android-sdk/platforms/       # Should show: android-35
ls ~/android-sdk/build-tools/     # Should show: 35.0.0
cat local.properties              # Should show: sdk.dir=/root/android-sdk
```

## Step 2: Build the debug APK

**Critical:** You must clear `JAVA_TOOL_OPTIONS` before building. The environment sets `JAVA_TOOL_OPTIONS` with direct proxy settings that bypass the local auth proxy, causing repository resolution failures.

```bash
# Kill any leftover proxy process on port 3128
kill $(lsof -t -i:3128) 2>/dev/null || true
sleep 1

# Build with JAVA_TOOL_OPTIONS cleared
JAVA_TOOL_OPTIONS="" ./scripts/run-with-proxy.sh assembleDebug
```

### Why `JAVA_TOOL_OPTIONS=""` is required

The container environment sets `JAVA_TOOL_OPTIONS` with `-Dhttp.proxyHost=<upstream> -Dhttp.proxyPort=<port>` pointing directly at the upstream proxy. Java's `HttpURLConnection` cannot handle proxy authentication for HTTPS CONNECT requests. The `run-with-proxy.sh` script starts a local auth proxy (`auth-proxy.py`) that injects credentials, but `JAVA_TOOL_OPTIONS` overrides the Gradle-passed proxy settings. Clearing it lets the script's `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3128` take effect.

## Build output

| Build type | APK location |
|------------|-------------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

The debug APK is ~64MB and can be installed directly on Android 10+ devices via `adb install` or by transferring the file to the device.

## Step 3: Create installable artifact (optional)

To commit the APK to git for distribution:

```bash
mkdir -p artifacts
cp app/build/outputs/apk/debug/app-debug.apk artifacts/
git add -f artifacts/app-debug.apk   # -f needed because *.apk is in .gitignore
git commit -m "chore(build): add debug APK artifact"
```

## Troubleshooting

### "Plugin was not found in any of the following sources"

The auth proxy is not working correctly. Ensure:
1. No stale proxy on port 3128: `kill $(lsof -t -i:3128) 2>/dev/null`
2. `JAVA_TOOL_OPTIONS` is cleared: `JAVA_TOOL_OPTIONS=""`
3. The proxy script is accessible: `ls scripts/auth-proxy.py`

### "Address already in use" on port 3128

A previous auth proxy instance is still running:
```bash
kill $(lsof -t -i:3128) 2>/dev/null
sleep 1
```

### "SDK location not found"

Create `local.properties`:
```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### Build succeeds but APK is missing

Check the exact output path:
```bash
find app/build/outputs -name "*.apk" -type f
```

## Full CI verification

Before pushing, run the full CI pipeline:
```bash
JAVA_TOOL_OPTIONS="" ./scripts/verify-local.sh
```

For quick style checks during development:
```bash
JAVA_TOOL_OPTIONS="" ./scripts/verify-local.sh --quick
```
