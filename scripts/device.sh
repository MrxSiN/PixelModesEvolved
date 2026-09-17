#!/usr/bin/env sh
# Install and diagnose ModeEvolved on a connected device (root and Vector required).
#
#   scripts/device.sh install   build, install, enable in Vector, set scope
#   scripts/device.sh logs      follow this module's log lines
#   scripts/device.sh rules     print the stored trigger rules
#   scripts/device.sh modes     show every Mode and whether it is active
#   scripts/device.sh check     one-shot health report
#
# Set ADB_SERIAL when more than one device is attached.
set -eu

ADB="${ADB:-/d/Android/platform-tools/adb.exe}${ADB_SERIAL:+ -s $ADB_SERIAL}"
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PACKAGE="my.github.MrxSiN.modeevolved"
CLI="su -c /data/adb/lspd/cli"
export MSYS_NO_PATHCONV=1

case "${1:-check}" in
    install)
        (cd "$ROOT" && ./gradlew.bat assembleRelease)
        $ADB install -r "$(cygpath -w "$ROOT/app/build/outputs/apk/release/ModeEvolved-v0.0.1.apk")"
        $ADB shell "$CLI modules enable $PACKAGE"
        $ADB shell "$CLI scope set $PACKAGE system/0 com.android.settings/0"
        $ADB shell am force-stop com.android.settings
        echo "Live now: system_server hot reloads, Settings restarts once hidden."
        ;;
    logs)
        $ADB logcat -v time -s ModeEvolved
        ;;
    rules)
        $ADB shell settings get secure mode_evolved_rules
        ;;
    modes)
        $ADB shell "su -c 'dumpsys notification --zen'" | sed -n '1,/mUser=/p' | tr ',' '\n' |
            grep -E "ZenRule\[id=|^ *name=|^ *state=|conditionOverride="
        ;;
    check)
        echo "== device";  $ADB shell getprop ro.build.fingerprint
        echo "== package"; $ADB shell pm path "$PACKAGE" || echo "not installed"
        echo "== vector";  $ADB shell "$CLI modules ls" | grep "$PACKAGE" || echo "not listed"
        $ADB shell "$CLI scope ls $PACKAGE"
        echo "== rules";   $ADB shell settings get secure mode_evolved_rules
        echo "== log";     $ADB logcat -d -s ModeEvolved | tail -n 40
        ;;
    *)
        echo "usage: $0 install|logs|rules|modes|check" >&2
        exit 2
        ;;
esac
