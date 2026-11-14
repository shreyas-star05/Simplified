#!/usr/bin/env bash
# Runs the Dusk app's tap-through tests on the CI emulator and collects screenshots and logs.
set -uo pipefail
mkdir -p ci-out/screens
status=0
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace 2>&1 | tee ci-out/emulator-log.txt
[ "${PIPESTATUS[0]}" -eq 0 ] || status=1
find app/build/outputs/connected_android_test_additional_output -name '*.png' -exec cp {} ci-out/screens/ \; 2>/dev/null || true
find app/build/outputs/androidTest-results -name '*.xml' -exec cp {} ci-out/ \; 2>/dev/null || true
adb logcat -d | grep -E "DuskTest|Dusk  |AndroidRuntime|FATAL" | tail -80 > ci-out/logcat.txt || true
exit $status
