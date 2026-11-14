#!/usr/bin/env bash
# Schedules two reminders, restarts the emulator, and checks both still fire on time.
# Reminders are set 8 and 9 minutes out so a slow emulator has time to finish starting up.
set -uo pipefail
PKG=com.dusk.lab
STORE=/data/user_de/0/$PKG/files/alarms.json
store() { adb shell run-as $PKG cat $STORE 2>/dev/null; }

./gradlew :app:installDebug --no-daemon -q
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am start -W -n $PKG/.MainActivity --ei schedule_minutes 8
START=$(date +%s)
sleep 5
echo "before restart:"; store; echo
echo "alarms the system holds for the app before restart:"
adb shell dumpsys alarm | grep -A3 "$PKG" | head -20

# Android records "this app has been opened" a few seconds after launch. If the phone restarts
# before that is saved, the app counts as never opened and gets no start-up signal.
for i in $(seq 1 30); do
  adb shell dumpsys package $PKG | grep -q "stopped=false" && break
  sleep 2
done
sleep 15
echo "app state before restart:"
adb shell dumpsys package $PKG | grep -oE "stopped=[a-z]+ notLaunched=[a-z]+" | head -1
adb shell sync

echo "restarting the emulator at $(date -u +%T)"
adb reboot
sleep 20
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
echo "booted $(( $(date +%s) - START ))s after scheduling"

# Wait for the app's start-up handler to put the reminders back (up to 7 minutes after scheduling).
SEEN=0
while [ $(( $(date +%s) - START )) -lt 420 ]; do
  if store | grep -q '"events":\["'; then SEEN=1; break; fi
  sleep 10
done
echo "start-up handler ran: $SEEN, $(( $(date +%s) - START ))s after scheduling"
echo "alarms the system holds for the app after restart:"
adb shell dumpsys alarm | grep -A3 "$PKG" | head -20
echo "app state:"
adb shell dumpsys package $PKG | grep -oE "stopped=[a-z]+ notLaunched=[a-z]+" | head -1

# The second reminder is due 9 minutes after scheduling. Wait until 45 seconds past that.
REMAINING=$(( START + 585 - $(date +%s) ))
[ "$REMAINING" -gt 0 ] && sleep "$REMAINING"

store > ci-out/alarms-after-restart.json
echo "after restart:"; cat ci-out/alarms-after-restart.json; echo
echo "log lines from the app and boot broadcasts:"
adb logcat -d | grep -iE "DuskLab|com\.dusk\.lab.*(BOOT|Receiver)|BOOT_COMPLETED" | tail -40
adb shell cmd statusbar expand-notifications || true
sleep 2
adb exec-out screencap -p > ci-out/screens/10-after-restart-notifications.png || true
adb shell cmd statusbar collapse || true

python3 ci/check_restart.py ci-out/alarms-after-restart.json
