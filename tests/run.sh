#!/usr/bin/env bash
# Tests for tvcleanse.sh using a fake adb. Run: bash tests/run.sh
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
export MOCK_DIR="$(mktemp -d)"
export TVCLEANSE_HOME="$(mktemp -d)"
BIN="$(mktemp -d)"; cp "$HERE/mock-adb" "$BIN/adb"; chmod +x "$BIN/adb"
export PATH="$BIN:$PATH"
T="bash $HERE/../tvcleanse.sh 10.0.0.9"
pass=0; fail=0
check() { # name, condition-exit
  if [ "$2" -eq 0 ]; then echo "ok   $1"; pass=$((pass+1)); else echo "FAIL $1"; fail=$((fail+1)); fi
}
out="$($T info)";                         echo "$out" | grep -q "55S451G";       check "info shows model" $?
out="$($T mem)";                          echo "$out" | grep -q "available: 371"; check "mem reads MemAvailable" $?
out="$($T scan)"
echo "$out" | grep -E "com.android.systemui" | grep -q BLOCKED;                 check "scan blocks core package" $?
echo "$out" | grep -E "com.tcl.setupwizard" | grep -q BLOCKED;                  check "scan blocks setup package" $?
echo "$out" | grep -E "com.example.homeapp" | grep -q BLOCKED;                  check "scan blocks home app" $?
echo "$out" | grep -E "com.vendor.adsrow" | grep -q " ok ";                     check "scan allows ordinary package" $?
out="$($T disable com.android.systemui com.tcl.setupwizard com.vendor.persistentthing com.vendor.nothere 2>&1)"
echo "$out" | grep -q "REFUSE com.android.systemui";                            check "disable refuses core" $?
echo "$out" | grep -q "REFUSE com.tcl.setupwizard";                             check "disable refuses setup" $?
echo "$out" | grep -q "REFUSE com.vendor.persistentthing.*PERSISTENT";          check "disable refuses PERSISTENT" $?
echo "$out" | grep -q "not installed";                                          check "disable skips missing" $?
[ ! -s "$MOCK_DIR/disabled" ];                                                  check "refused packages not disabled" $?
$T disable com.vendor.adsrow com.vendor.promos >/dev/null 2>&1
grep -qx com.vendor.adsrow "$MOCK_DIR/disabled";                                check "disable turns off ordinary package" $?
$T log | grep -q "disable com.vendor.adsrow";                                   check "disable is logged" $?
$T anim off >/dev/null
grep -q "window_animation_scale=0" "$MOCK_DIR/settings";                        check "anim off sets 0" $?
$T undo >/dev/null
[ ! -s "$MOCK_DIR/disabled" ];                                                  check "undo re-enables packages" $?
grep -q "window_animation_scale" "$MOCK_DIR/settings" && ! grep -q "window_animation_scale=0" "$MOCK_DIR/settings"; r=$?
[ $r -eq 0 ] || ! grep -q "window_animation_scale=0" "$MOCK_DIR/settings";      check "undo restores animation" $?
[ ! -s "$TVCLEANSE_HOME"/*.log ];                                               check "log empty after clean undo" $?
# home screen install / remove
echo "fake apk" > "$MOCK_DIR/fake.apk"
out="$(TVCLEANSE_APK="$MOCK_DIR/fake.apk" $T home-install 2>&1)"
echo "$out" | grep -q "now the default";                                        check "home-install makes it default" $?
[ "$(cat "$MOCK_DIR/homepkg")" = "com.tvcleanse.home" ];                        check "home role set to our package" $?
grep -qx "com.example.homeapp" "$MOCK_DIR/packages";                            check "stock home screen left alone" $?
$T log | grep -q "^home com.tvcleanse.home";                                   check "home install is logged" $?
$T undo >/dev/null
! grep -qx "com.tvcleanse.home" "$MOCK_DIR/packages";                          check "undo removes our home screen" $?
out="$(TVCLEANSE_RELEASE_URL=http://127.0.0.1:9 $T home-install 2>&1)"; [ $? -ne 0 ]; check "bad download fails, installs nothing" $?
touch "$MOCK_DIR/offline"
$T info >/dev/null 2>&1;                                                        [ $? -ne 0 ]; check "unreachable TV fails cleanly" $?
echo; echo "$pass passed, $fail failed"; [ "$fail" -eq 0 ]
