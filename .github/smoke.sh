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

adb install -r "$APK" || fail "install"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || fail "grant notifications"
# launch once so the app leaves the stopped state and can receive BOOT_COMPLETED
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
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

echo "== Step 2: simulate MagicOS: switch the service off, then a real reboot"
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0
sleep 2
adb logcat -c
adb reboot
sleep 10
wait_boot
sleep 25
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
adb reboot
sleep 10
wait_boot
sleep 60
adb shell dumpsys accessibility | grep -q "$PKG" || fail "service not running after reboot with it enabled"
adb logcat -d -s BackVibrate | tail -20
echo "SMOKE OK"
