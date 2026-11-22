#!/usr/bin/env bash
# TV Cleanse: turn off bloat on an Android TV or Fire TV over adb, with an undo log.
#
# Usage: tvcleanse.sh <tv-ip-or-serial> <command> [args]
#
# Commands:
#   info                 show make, model, Android version
#   mem                  show free memory
#   scan                 list every package with a state and a safety guard
#   disable <pkg>...     turn packages off (never uninstalls), logged for undo
#   anim off|on          turn system animations off or back on
#   home-install         install the TV Cleanse home screen and make it the default
#   home-remove          remove it (the TV's own home screen comes back)
#   undo                 reverse everything in the log, newest first
#   log                  show what has been changed
#   finish               turn adb debugging off (best effort) and disconnect
#
# Nothing here uninstalls or deletes anything. `disable` uses
# `pm disable-user --user 0`, which `pm enable` reverses.

set -u

LOG_DIR="${TVCLEANSE_HOME:-$HOME/.tvcleanse}"

die() { echo "error: $*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb not found. Install Android Platform Tools: https://developer.android.com/tools/releases/platform-tools"

[ $# -ge 2 ] || { sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

TARGET="$1"; CMD="$2"; shift 2

# An IP with no port gets the standard adb port.
case "$TARGET" in
  *:*) SERIAL="$TARGET" ;;
  *[!0-9.]*) SERIAL="$TARGET" ;;
  *) SERIAL="$TARGET:5555" ;;
esac

mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/$(printf '%s' "$SERIAL" | tr -c 'A-Za-z0-9._-' '_').log"
touch "$LOG"

# Run a command on the TV. Strips Windows line endings.
tv() { adb -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }

connect() {
  case "$SERIAL" in
    *:*) adb connect "$SERIAL" >/dev/null 2>&1 ;;
  esac
  local state
  state="$(adb -s "$SERIAL" get-state 2>&1 | tr -d '\r')"
  case "$state" in
    device) return 0 ;;
    *unauthorized*) die "TV has not approved this computer. Look at the TV and press Allow on the 'Allow debugging?' prompt, then run again." ;;
    *) die "cannot reach $SERIAL ($state). Same Wi-Fi? USB/Network debugging on?" ;;
  esac
}

# ---------------------------------------------------------------------------
# Safety guard. Anything matching is refused. This errs on the side of refusing.
# ---------------------------------------------------------------------------

# Package-name prefixes that are core to Android and Google services.
never_prefix() {
  case "$1" in
    android|android.*) return 0 ;;
    com.android.*) return 0 ;;
    com.google.android.gms|com.google.android.gms.*) return 0 ;;
    com.google.android.gsf|com.google.android.gsf.*) return 0 ;;
    com.google.android.packageinstaller|com.google.android.permissioncontroller) return 0 ;;
    com.google.android.inputmethod.*) return 0 ;;
    com.amazon.tv.launcher|com.amazon.tv.settings|com.amazon.device.software.ota) return 0 ;;
  esac
  return 1
}

# Words in a package name that mean "this keeps the TV working".
never_keyword() {
  local lower
  lower="$(printf '%s' "$1" | tr 'A-Z' 'a-z')"
  case "$lower" in
    *launcher*|*home*|*setup*|*provision*|*wizard*|*settings*|*ota*|*updater*|*update*) return 0 ;;
    *wifi*|*wlan*|*network*|*bluetooth*|*tvinput*|*tvprovider*|*hdmi*|*cec*) return 0 ;;
    *inputmethod*|*keyboard*|*.ime|*.ime.*|*remote*|*vending*|*systemui*) return 0 ;;
  esac
  return 1
}

# Echo the package that currently handles the Home button, if we can tell.
home_package() {
  tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | tail -n 1 | sed 's|/.*||'
}

# Returns 0 and prints a reason if the package must not be disabled.
guard_reason() {
  local pkg="$1" flags home
  if never_prefix "$pkg"; then echo "core Android/Google/home package"; return 0; fi
  if never_keyword "$pkg"; then echo "name suggests it keeps the TV working"; return 0; fi
  home="$(home_package)"
  if [ -n "$home" ] && [ "$pkg" = "$home" ]; then echo "it is the current home screen"; return 0; fi
  flags="$(tv dumpsys package "$pkg" | grep -m1 'flags=')"
  case "$flags" in
    *PERSISTENT*) echo "Android keeps it running (PERSISTENT)"; return 0 ;;
  esac
  return 1
}

package_exists() { tv pm list packages | sed 's/^package://' | grep -qx "$1"; }
is_disabled()    { tv pm list packages -d | sed 's/^package://' | grep -qx "$1"; }

# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

cmd_info() {
  echo "maker:   $(tv getprop ro.product.manufacturer)"
  echo "model:   $(tv getprop ro.product.model)"
  echo "android: $(tv getprop ro.build.version.release) (sdk $(tv getprop ro.build.version.sdk))"
  if [ "$(tv getprop ro.product.manufacturer | tr 'A-Z' 'a-z')" = "amazon" ] \
     || tv pm list packages | grep -q 'package:com.amazon.tv.launcher'; then
    echo "family:  Fire TV (Fire OS)"
  else
    echo "family:  Android TV / Google TV"
  fi
}

cmd_mem() {
  tv cat /proc/meminfo | awk '
    /^MemTotal:/     {t=$2}
    /^MemAvailable:/ {a=$2}
    END {
      if (t == "") { print "could not read memory"; exit 1 }
      printf "total:     %d MB\n", t/1024
      printf "available: %d MB\n", a/1024
    }'
}

cmd_scan() {
  local on off user home
  on="$(tv pm list packages | sed 's/^package://' | sort)"
  off="$(tv pm list packages -d | sed 's/^package://' | sort)"
  user="$(tv pm list packages -3 | sed 's/^package://' | sort)"
  home="$(home_package)"
  printf '%-9s %-7s %-9s %s\n' STATE TYPE GUARD PACKAGE
  local pkg state type guard
  while IFS= read -r pkg; do
    [ -n "$pkg" ] || continue
    state=on;   printf '%s\n' "$off"  | grep -qx "$pkg" && state=off
    type=system; printf '%s\n' "$user" | grep -qx "$pkg" && type=user
    guard=ok
    if never_prefix "$pkg" || never_keyword "$pkg" || [ "$pkg" = "$home" ]; then guard=BLOCKED; fi
    printf '%-9s %-7s %-9s %s\n' "$state" "$type" "$guard" "$pkg"
  done <<EOF
$on
EOF
  echo
  echo "BLOCKED = this tool will refuse to disable it. 'ok' only means the quick checks passed;"
  echo "'disable' also checks the PERSISTENT flag. Look up anything you do not recognise first."
}

cmd_disable() {
  [ $# -ge 1 ] || die "give at least one package name"
  local pkg reason failed=0
  for pkg in "$@"; do
    if ! package_exists "$pkg"; then echo "skip   $pkg: not installed"; failed=1; continue; fi
    if is_disabled "$pkg"; then echo "skip   $pkg: already off"; continue; fi
    if reason="$(guard_reason "$pkg")"; then echo "REFUSE $pkg: $reason"; failed=1; continue; fi
    tv pm disable-user --user 0 "$pkg" >/dev/null
    if is_disabled "$pkg"; then
      printf 'disable %s %s\n' "$pkg" "$(date +%s)" >> "$LOG"
      echo "off    $pkg"
      sleep 1
      if [ "$(adb -s "$SERIAL" get-state 2>/dev/null | tr -d '\r')" != "device" ]; then
        echo "WARNING: TV stopped answering after turning off $pkg. Undo it: $0 $TARGET undo" >&2
        exit 3
      fi
    else
      echo "FAILED $pkg: TV did not report it as off"; failed=1
    fi
  done
  return $failed
}

ANIM_KEYS="window_animation_scale transition_animation_scale animator_duration_scale"

cmd_anim() {
  local mode="${1:-}" key old
  case "$mode" in
    off)
      for key in $ANIM_KEYS; do
        old="$(tv settings get global "$key")"
        [ -n "$old" ] || old=null
        printf 'anim %s %s\n' "$key" "$old" >> "$LOG"
        tv settings put global "$key" 0
      done
      echo "animations off"
      ;;
    on)
      for key in $ANIM_KEYS; do tv settings put global "$key" 1.0; done
      echo "animations on"
      ;;
    *) die "usage: anim off|on" ;;
  esac
}

cmd_log() { [ -s "$LOG" ] && cat "$LOG" || echo "nothing logged for $SERIAL"; }

cmd_undo() {
  [ -s "$LOG" ] || { echo "nothing to undo for $SERIAL"; return 0; }
  local keep line kind a b rc=0
  keep="$(mktemp)"
  # Newest first.
  tac "$LOG" 2>/dev/null > "$LOG.rev" || awk '{a[NR]=$0} END{for(i=NR;i>0;i--)print a[i]}' "$LOG" > "$LOG.rev"
  while IFS= read -r line; do
    kind="${line%% *}"; line="${line#* }"; a="${line%% *}"; b="${line#* }"
    case "$kind" in
      disable)
        tv pm enable "$a" >/dev/null
        if is_disabled "$a"; then
          echo "FAILED to turn $a back on"; printf '%s %s %s\n' "$kind" "$a" "$b" >> "$keep"; rc=1
        else
          echo "on     $a"
        fi ;;
      home)
        if cmd_home_remove >/dev/null; then echo "removed home screen $a"; else
          printf '%s %s %s\n' "$kind" "$a" "$b" >> "$keep"; rc=1; fi ;;
      anim)
        if [ "$b" = "null" ]; then tv settings delete global "$a" >/dev/null; else tv settings put global "$a" "$b"; fi
        echo "restored $a" ;;
    esac
  done < "$LOG.rev"
  rm -f "$LOG.rev"
  # Keep failures (restored to original order) so a later undo can retry them.
  if [ -s "$keep" ]; then
    tac "$keep" 2>/dev/null > "$LOG" || awk '{a[NR]=$0} END{for(i=NR;i>0;i--)print a[i]}' "$keep" > "$LOG"
  else
    : > "$LOG"
  fi
  rm -f "$keep"
  return $rc
}

cmd_finish() {
  echo "Turning USB debugging off (best effort). If the TV still shows it on, turn it off:"
  echo "  Settings > Device Preferences > Developer options > USB debugging."
  tv settings put global adb_enabled 0 >/dev/null
  adb disconnect "$SERIAL" >/dev/null 2>&1
  echo "disconnected"
}

HOME_PKG="com.tvcleanse.home"
RELEASE_URL="${TVCLEANSE_RELEASE_URL:-https://github.com/gblanchard77-beep/tv-cleanse/releases/latest/download}"

# Get the APK: use TVCLEANSE_APK if set, otherwise download it and check its checksum.
fetch_apk() {
  if [ -n "${TVCLEANSE_APK:-}" ]; then
    [ -f "$TVCLEANSE_APK" ] || die "TVCLEANSE_APK not found: $TVCLEANSE_APK"
    APK_PATH="$TVCLEANSE_APK"; return 0
  fi
  command -v curl >/dev/null 2>&1 || die "curl not found (or build the APK yourself and set TVCLEANSE_APK)"
  local dir; dir="$(mktemp -d)"
  curl -fsSL -o "$dir/tvcleanse-home.apk" "$RELEASE_URL/tvcleanse-home.apk" \
    || die "could not download the APK from $RELEASE_URL (no release yet? build it: see README)"
  curl -fsSL -o "$dir/tvcleanse-home.apk.sha256" "$RELEASE_URL/tvcleanse-home.apk.sha256" \
    || die "could not download the checksum file"
  ( cd "$dir" && sha256sum -c tvcleanse-home.apk.sha256 >/dev/null 2>&1 ) \
    || die "checksum did not match. Not installing."
  APK_PATH="$dir/tvcleanse-home.apk"
}

GOOGLE_TV_HOME_PKG="com.google.android.apps.tv.launcherx"
HELPER="$HOME_PKG/.HomeRedirect"
HELPER_LONG="$HOME_PKG/$HOME_PKG.HomeRedirect"

# The app in front right now, like com.example/.Activity -> com.example
front_package() {
  tv dumpsys window | grep -m1 mCurrentFocus | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | head -n 1 | sed 's|/.*||'
}

# Turn the "TV Cleanse Home button" accessibility helper on or off, keeping any others.
helper_on() {
  local cur
  cur="$(tv settings get secure enabled_accessibility_services)"
  case "$cur" in null) cur="" ;; esac
  case ":$cur:" in
    *":$HELPER:"*|*":$HELPER_LONG:"*) ;;
    *) cur="${cur:+$cur:}$HELPER" ;;
  esac
  tv settings put secure enabled_accessibility_services "$cur" >/dev/null
  tv settings put secure accessibility_enabled 1 >/dev/null
}

helper_bound() { tv dumpsys accessibility | grep -q "Bound services:.*TV Cleanse Home button"; }

helper_off() {
  local cur keep="" s
  cur="$(tv settings get secure enabled_accessibility_services)"
  case ":$cur:" in *":$HELPER:"*|*":$HELPER_LONG:"*) ;; *) return 0 ;; esac
  local IFS=:
  for s in $cur; do
    case "$s" in ""|null|"$HELPER"|"$HELPER_LONG") ;; *) keep="${keep:+$keep:}$s" ;; esac
  done
  if [ -n "$keep" ]; then
    tv settings put secure enabled_accessibility_services "$keep" >/dev/null
  else
    tv settings delete secure enabled_accessibility_services >/dev/null
    tv settings put secure accessibility_enabled 0 >/dev/null
  fi
}

cmd_home_install() {
  local out sdk now
  fetch_apk
  out="$(adb -s "$SERIAL" install -r "$APK_PATH" 2>&1 | tr -d '\r')"
  case "$out" in
    *Success*) ;;
    *INCOMPATIBLE*|*signatures*)
      echo "An older copy is signed differently. Replacing it."
      adb -s "$SERIAL" uninstall "$HOME_PKG" >/dev/null 2>&1
      out="$(adb -s "$SERIAL" install "$APK_PATH" 2>&1 | tr -d '\r')"
      case "$out" in *Success*) ;; *) die "install failed: $out" ;; esac ;;
    *) die "install failed: $out" ;;
  esac
  printf 'home %s %s\n' "$HOME_PKG" "$(date +%s)" >> "$LOG"

  # Make it the default home screen. The TV's own home screen is NOT disabled.
  sdk="$(tv getprop ro.build.version.sdk)"
  if [ "${sdk:-0}" -ge 29 ] 2>/dev/null; then
    tv cmd role add-role-holder --user 0 android.app.role.HOME "$HOME_PKG" >/dev/null
  else
    tv cmd package set-home-activity "$HOME_PKG/.MainActivity" >/dev/null
  fi
  tv input keyevent KEYCODE_HOME >/dev/null
  sleep 2
  now="$(home_package)"
  if [ "$now" = "$HOME_PKG" ]; then
    echo "TV Cleanse home screen is now the default."
  elif [ "$now" = "$GOOGLE_TV_HOME_PKG" ]; then
    # Google TV's home always wins and must stay on. Use the helper instead of switching it off.
    echo "This is a Google TV. Its own home screen stays on; turning on the TV Cleanse Home button helper."
    # Open TV Cleanse once first: Android 14 won't start the helper of an app that has never run.
    tv am start -n "$HOME_PKG/.MainActivity" >/dev/null
    sleep 2
    helper_on
    # Wait for Android to start the helper, then open TV Cleanse once. The helper reacts when
    # Google TV's screen opens, so Home does nothing while Google TV is already on screen.
    # Android can ignore the setting if it lands while the new app is still being registered
    # (seen on Android 14), so if the helper hasn't started, switch it off and on once more.
    local try round
    for round in 1 2; do
      for try in 1 2 3 4 5 6 7 8; do
        helper_bound && break 2
        sleep 1
      done
      [ "$round" = 1 ] && { helper_off; sleep 2; helper_on; }
    done
    tv am start -n "$HOME_PKG/.MainActivity" >/dev/null
    sleep 2
    tv input keyevent KEYCODE_HOME >/dev/null
    local front=""
    for try in 1 2 3 4 5 6; do
      sleep 1
      front="$(front_package)"
      [ "$front" = "$HOME_PKG" ] && break
    done
    if [ "$front" = "$HOME_PKG" ]; then
      echo "Home now opens TV Cleanse. Hold Back on it to reach Google TV's home."
    else
      echo "The helper is on, but Home still showed: ${front:-nothing yet}."
      echo "On the TV, check Settings > Accessibility > TV Cleanse Home button is on."
    fi
  else
    echo "Installed, but the Home button still opens: ${now:-unknown}."
    echo "Press Home on the remote; if the TV asks which home screen to use, choose TV Cleanse Home."
  fi
  echo "The TV's own home screen is still installed. Undo: $0 $TARGET home-remove"
}

cmd_home_remove() {
  helper_off
  tv cmd role remove-role-holder --user 0 android.app.role.HOME "$HOME_PKG" >/dev/null 2>&1
  adb -s "$SERIAL" uninstall "$HOME_PKG" >/dev/null 2>&1
  if package_exists "$HOME_PKG"; then
    echo "FAILED to remove $HOME_PKG"; return 1
  fi
  grep -v "^home $HOME_PKG " "$LOG" > "$LOG.n"; mv "$LOG.n" "$LOG"
  tv input keyevent KEYCODE_HOME >/dev/null
  echo "removed. Home button now opens: $(home_package)"
}

connect
case "$CMD" in
  info)    cmd_info ;;
  mem)     cmd_mem ;;
  scan)    cmd_scan ;;
  disable) cmd_disable "$@" ;;
  anim)    cmd_anim "$@" ;;
  undo)    cmd_undo ;;
  home-install) cmd_home_install ;;
  home-remove)  cmd_home_remove ;;
  log)     cmd_log ;;
  finish)  cmd_finish ;;
  *)       die "unknown command: $CMD" ;;
esac
