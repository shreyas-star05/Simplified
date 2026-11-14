#!/usr/bin/env bash
# Runs on the CI emulator: tap-through tests with screenshots, then a real restart test.
set -uo pipefail
mkdir -p ci-out/screens
status=0

./gradlew :lab:connectedDebugAndroidTest --no-daemon --stacktrace 2>&1 | tee ci-out/emulator-log.txt
[ "${PIPESTATUS[0]}" -eq 0 ] || status=1

find lab/build/outputs/connected_android_test_additional_output -name '*.png' -exec cp {} ci-out/screens/ \; 2>/dev/null || true
find lab/build/outputs/androidTest-results -name '*.xml' -exec cp {} ci-out/ \; 2>/dev/null || true

bash ci/restart-test.sh 2>&1 | tee ci-out/restart-test.txt
[ "${PIPESTATUS[0]}" -eq 0 ] || status=1

exit $status
