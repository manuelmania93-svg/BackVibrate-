#!/usr/bin/env bash
# Emulator smoke test for the reboot behaviour. Runs inside android-emulator-runner.
set -u
PKG=com.example.backvibrate
SVC="$PKG/$PKG.BackVibrateAccessibilityService"
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
fail() { echo "SMOKE FAIL: $*"; adb logcat -d -s BackVibrate AndroidRuntime | tail -60; exit 1; }
wait_boot() {
  adb wait-for-device
  for i in $(seq 1 90); do
    [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && return 0
    sleep 2
  done
  fail "emulator did not finish booting"
}

# Tap a view by resource-id suffix using the uiautomator dump (real UI interaction).
tap_view() {
  local rid="$1" xy
  adb shell uiautomator dump /sdcard/ui-dump.xml >/dev/null 2>&1 || return 1
  adb shell "cat /sdcard/ui-dump.xml" > /tmp/ui-dump.xml || return 1
  xy=$(python3 - "$rid" <<'PY'
import sys, re
rid = sys.argv[1]
s = open('/tmp/ui-dump.xml', encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node[^>]*>', s):
    tag = m.group(0)
    if ('id/' + rid + '"') in tag:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
) || return 1
  adb shell input tap $xy || return 1
}

# Wait until the accessibility service is actually running: its ServiceRecord must
# carry a live process (app=ProcessRecord), not just exist as a record.
wait_bound() {
  local i
  for i in $(seq 1 45); do
    if adb shell dumpsys activity services $PKG | grep -A15 "BackVibrateAccessibilityService" | grep -q "app=ProcessRecord"; then return 0; fi
    sleep 2
  done
  adb shell dumpsys activity services $PKG | tail -20
  return 1
}

OLD=$(ls old-apk/*.apk 2>/dev/null | head -1)
if [ -n "$OLD" ]; then
  echo "== Step 0: install previous build, grant, then update over it"
  adb install "$OLD" || fail "install old build"
  adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || fail "grant WRITE_SECURE_SETTINGS on old build"
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 2
fi
adb install -r "$APK" || fail "install"
if [ -n "$OLD" ]; then
  adb shell dumpsys package $PKG | grep -q "android.permission.WRITE_SECURE_SETTINGS: granted=true" || fail "WRITE_SECURE_SETTINGS lost after install-over update"
  echo "UPDATE OVER OLD BUILD OK: WRITE_SECURE_SETTINGS preserved"
fi
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || fail "grant notifications"
# launch once so the app leaves the stopped state and can receive BOOT_COMPLETED
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
adb exec-out screencap -p > "${GITHUB_WORKSPACE:-/tmp}/bv-ui-before-grant.png" 2>/dev/null || true
# master switch OFF binding via real UI toggle, then verify the pref on disk; restore ON the same way
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
tap_view "switchVibration" || fail "master switch not found in UI for OFF toggle"
sleep 1
adb shell "run-as $PKG cat shared_prefs/back_vibrate_prefs.xml" | grep -q 'vibration_enabled" value="false"' || fail "UI toggle did not write OFF pref"
adb exec-out screencap -p > "${GITHUB_WORKSPACE:-/tmp}/bv-ui-vibration-off.png" || fail "OFF screenshot capture"
tap_view "switchVibration" || fail "master switch not found in UI for ON restore"
sleep 1
adb shell "run-as $PKG cat shared_prefs/back_vibrate_prefs.xml" | grep -q 'vibration_enabled" value="true"' || fail "UI toggle did not restore ON pref"
adb shell cmd notification allow_listener >/dev/null 2>&1 || true

echo "== Step 1: enable the service and check it binds and shows the overlay"
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
RUN=0
for i in $(seq 1 20); do
  if adb shell dumpsys accessibility | grep -q "$PKG"; then RUN=1; break; fi
  sleep 1
done
[ $RUN = 1 ] || fail "service did not bind"
adb shell dumpsys window windows | grep -q "TYPE_ACCESSIBILITY_OVERLAY\|type=2032" && echo "overlay window present" || echo "note: overlay window not found in dumpsys (not fatal)"

echo "== Step 2: simulate MagicOS without the grant: switch the service off, then a real reboot"
adb shell pm revoke $PKG android.permission.WRITE_SECURE_SETTINGS
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
sleep 2
adb logcat -c
adb shell sync
sleep 12
adb reboot
sleep 10
wait_boot
sleep 25
echo "--- diagnostics"
adb shell dumpsys package $PKG | grep -i "POST_NOTIFICATIONS" | head -3
adb shell dumpsys notification --noredact | grep -i "backvibrate" | head -10
adb shell cmd appops get $PKG POST_NOTIFICATION
echo "--- end diagnostics"
NOTI=$(adb shell dumpsys notification --noredact | grep -c "BackVibrate ist aus")
echo "recovery notification hits: $NOTI"
adb logcat -d -s BackVibrate | tail -20
[ "$NOTI" -ge 1 ] || fail "no recovery notification after reboot with service off"

echo "== Step 3: tap the notification target and check it resolves"
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null || fail "accessibility settings intent"

echo "== Step 4: service enabled before reboot must come back and no warning should stay"
adb shell cmd notification cancel >/dev/null 2>&1 || true
adb shell service call notification 1 >/dev/null 2>&1 || true
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
sleep 3
adb logcat -c
adb shell sync
sleep 12
adb reboot
sleep 10
wait_boot
sleep 60
adb shell dumpsys accessibility | grep -q "$PKG" || fail "service not running after reboot with it enabled"
adb logcat -d -s BackVibrate | tail -20
NOTI2=$(adb shell dumpsys notification --noredact | grep -c "BackVibrate läuft nicht")
[ "$NOTI2" -eq 0 ] || fail "false 'läuft nicht' warning although service runs"
echo "SMOKE OK"

echo "== Step 4: one-time WRITE_SECURE_SETTINGS grant, then a MagicOS-style kill + reboot must self-heal"
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || fail "grant WRITE_SECURE_SETTINGS"
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb logcat -c
adb shell sync
sleep 12
adb reboot
sleep 10
wait_boot
OK=0
ENA=""
for i in $(seq 1 20); do
  ENA=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
  case "$ENA" in *"$PKG"*) OK=1; break;; esac
  sleep 3
done
[ "$OK" = 1 ] || fail "self re-enable did not restore the service after reboot (enabled=$ENA)"
wait_bound || fail "service listed as enabled but not bound after self re-enable"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
adb exec-out screencap -p > "${GITHUB_WORKSPACE:-/tmp}/bv-ui-auto-on.png" 2>/dev/null || true
adb logcat -d -s BackVibrate | grep -i "self re-enable" | tail -3
echo "SMOKE SELF-REENABLE OK"

echo "== Step 5: master OFF via UI must survive a real reboot while the service still auto-reenables"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
tap_view "switchVibration" || fail "master switch not found in UI for OFF leg"
sleep 1
adb shell "run-as $PKG cat shared_prefs/back_vibrate_prefs.xml" | grep -q 'vibration_enabled" value="false"' || fail "UI toggle did not write OFF pref"
adb shell sync
sleep 12
adb reboot
sleep 10
wait_boot
OK=0
for i in $(seq 1 20); do
  ENA=$(adb shell settings get secure enabled_accessibility_services | tr -d '
')
  case "$ENA" in *"$PKG"*) OK=1; break;; esac
  sleep 3
done
[ "$OK" = 1 ] || fail "service setting did not survive the OFF-leg reboot (enabled=$ENA)"
adb shell run-as $PKG cat shared_prefs/back_vibrate_prefs.xml | grep -q 'vibration_enabled" value="false"' || fail "master OFF pref lost across reboot"
wait_bound || fail "service enabled but no live process within 90s on the OFF leg"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
adb shell uiautomator dump /sdcard/ui-dump.xml >/dev/null 2>&1 || true
adb shell "cat /sdcard/ui-dump.xml" 2>/dev/null | grep -q 'text="AKTIV"' || { adb exec-out screencap -p > "${GITHUB_WORKSPACE:-/tmp}/bv-ui-off-after-reboot.png"; fail "UI does not show AKTIV on the OFF leg after binding (screenshot kept as evidence)"; }
adb exec-out screencap -p > "${GITHUB_WORKSPACE:-/tmp}/bv-ui-off-after-reboot.png" || fail "off-after-reboot screenshot capture"
tap_view "switchVibration" || true
sleep 1
adb shell "run-as $PKG cat shared_prefs/back_vibrate_prefs.xml" | grep -q 'vibration_enabled" value="true"' || true
echo "OFF PERSISTED ACROSS REBOOT OK (service auto-reenabled, vibration pref stayed off)"
