#!/usr/bin/env sh
# Records the host build of the connected phone for HostCompatibilityTest: for every class
# the feature contracts name, what it extends and the members the contracts use, and what
# each structural fallback would find. Names only; no host code or APK is copied.
#
# Needs a rooted phone with Vector, and this module enabled for system and Settings.
# Installs a fresh build (system_server hot reloads it; Settings restarts), so run it while
# the phone is not in use.
#
#   scripts/capture-host-fixture.sh
#
# Writes app/src/test/resources/hosts/<Build.ID>/{com.android.settings,system_server}.txt.
# Add the build to VerifiedBuilds once its features are checked on the phone.
#
# Set ADB_SERIAL when more than one device is attached.
set -eu

ADB="${ADB:-/d/Android/platform-tools/adb.exe}${ADB_SERIAL:+ -s $ADB_SERIAL}"
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
FLAG=pixel_modes_evolved_capture
FILE=pixel_modes_evolved_host.txt
SETTINGS_FILE=/data/user_de/0/com.android.settings/files/$FILE
SYSTEM_FILE=/data/system/$FILE
export MSYS_NO_PATHCONV=1

shell() { $ADB shell "$@" | tr -d '\r'; }

BUILD=$(shell getprop ro.build.id)
OUT="$ROOT/app/src/test/resources/hosts/$BUILD"
mkdir -p "$OUT"

shell su -c "rm -f $SETTINGS_FILE $SYSTEM_FILE"
shell settings put global "$FLAG" 1
trap 'shell settings delete global "$FLAG" >/dev/null' EXIT

# A new versionCode makes system_server hot reload, which describes it; Settings describes
# itself the next time it starts.
"$ROOT/scripts/device.sh" install
shell am start -a android.settings.ZEN_MODE_SETTINGS >/dev/null

wait_for() {
    TRIES=0
    until [ "$(shell su -c "test -s $1 && echo yes")" = yes ] || [ $TRIES -ge 60 ]; do
        TRIES=$((TRIES + 1))
        sleep 1
    done
}

for HOST in com.android.settings system_server; do
    if [ "$HOST" = system_server ]; then SOURCE=$SYSTEM_FILE; else SOURCE=$SETTINGS_FILE; fi
    wait_for "$SOURCE"
    shell su -c "cat $SOURCE" > "$OUT/$HOST.txt"
    [ -s "$OUT/$HOST.txt" ] || { echo "$HOST wrote no description; is the module enabled for it?" >&2; exit 1; }
    echo "Recorded $HOST on $BUILD: $(grep -c '^class ' "$OUT/$HOST.txt") classes, $(grep -c '^absent ' "$OUT/$HOST.txt" || true) absent"
done
shell input keyevent KEYCODE_HOME
