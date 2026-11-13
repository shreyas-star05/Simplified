#!/usr/bin/env bash
# Schedules two reminders, restarts the emulator, and checks both still fire on time.
set -euo pipefail
PKG=com.dusk.lab
STORE=/data/user_de/0/$PKG/files/alarms.json

./gradlew :app:installDebug --no-daemon -q
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am start -W -n $PKG/.MainActivity --ei schedule_minutes 3
START=$(date +%s)
sleep 5
echo "before restart:"
adb shell run-as $PKG cat $STORE
echo

echo "restarting the emulator"
adb reboot
sleep 15
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
echo "booted $(( $(date +%s) - START ))s after scheduling"

# The second reminder is due 4 minutes after scheduling. Wait until 45 seconds past that.
REMAINING=$(( START + 285 - $(date +%s) ))
[ "$REMAINING" -gt 0 ] && sleep "$REMAINING"

adb shell run-as $PKG cat $STORE > ci-out/alarms-after-restart.json
echo "after restart:"
cat ci-out/alarms-after-restart.json
echo
adb shell cmd statusbar expand-notifications || true
sleep 2
adb exec-out screencap -p > ci-out/screens/10-after-restart-notifications.png || true
adb shell cmd statusbar collapse || true

python3 ci/check_restart.py ci-out/alarms-after-restart.json
