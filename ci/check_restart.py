"""Fails unless both restart-test reminders fired within a minute of their time after a restart."""
import json
import sys

data = json.load(open(sys.argv[1]))
alarms = [a for a in data["alarms"] if a["label"] == "restart test"]
problems = []
if len(alarms) != 2:
    problems.append(f"expected 2 restart-test reminders, found {len(alarms)}")
for a in alarms:
    if "firedAt" not in a:
        problems.append(f"#{a['id']} ({a['api']}) never fired")
        continue
    late = (a["firedAt"] - a["scheduledAt"]) / 1000
    print(f"#{a['id']} {a['api']}: fired {late:.1f}s from its time, re-set after restart x{a.get('reschedules', 0)}, note: {a.get('note', '')!r}")
    if not -1 <= late <= 60:
        problems.append(f"#{a['id']} ({a['api']}) fired {late:.1f}s from its time")
    if a.get("reschedules", 0) < 1:
        problems.append(f"#{a['id']} ({a['api']}) was not re-set after the restart")
if not any("restart" in e for e in data.get("events", [])):
    problems.append("no restart event was recorded")
print("events:", data.get("events", []))
if problems:
    print("FAILED:", *problems, sep="\n  ")
    sys.exit(1)
print("PASSED: both reminders survived the restart and fired on time")
