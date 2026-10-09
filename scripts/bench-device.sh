#!/usr/bin/env sh
# On-device cost of the automation inside system_server (root required). PERFORMANCE.md and
# POWER_AUDIT.md say what each number means and what baseline it is compared with.
#
#   scripts/bench-device.sh idle [seconds]    CPU time and runs of the automation thread
#   scripts/bench-device.sh providers         which location requests, sensors and callbacks it holds
#   scripts/bench-device.sh wifi [cycles]     toggle Wi-Fi, CPU per cycle (not over adb-over-Wi-Fi)
#   scripts/bench-device.sh trace [seconds]   Perfetto trace of the automation thread and its wakers
#
# Run on the baseline APK and on the rewrite, same rules, same device state.
# Set ADB_SERIAL when more than one device is attached.
set -eu

ADB="${ADB:-/d/Android/platform-tools/adb.exe}${ADB_SERIAL:+ -s $ADB_SERIAL}"
HELPER=/data/local/tmp/pme-bench.sh
export MSYS_NO_PATHCONV=1

# Runs on the phone as root. The kernel keeps 15 characters of a thread name.
helper() {
    cat <<'EOF'
SS=$(pidof system_server)
tid() { grep -lx PixelModesEvolv /proc/$SS/task/*/comm | head -n 1 | cut -d/ -f5; }
case "$1" in
    tid) tid ;;
    # "<ns on cpu> <ns waiting> <times run>"
    schedstat) cat /proc/$SS/task/$(tid)/schedstat ;;
    providers)
        echo "== location"; dumpsys location | grep -iE "PixelModesEvolved" || echo none
        echo "== sensors (system_server)"; dumpsys sensorservice | grep -E "pid=$SS .*Significant" | tail -n 2 || echo none
        echo "== alarms"; dumpsys alarm | grep -ic pixelmodes
        echo "== wakelocks"; dumpsys power | grep -ic pixelmodes
        ;;
esac
EOF
}

# adb.exe on Windows needs a Windows path for a local file.
local_path() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
WORK=$(mktemp -d)

on_phone() { $ADB shell "su -c 'sh $HELPER $1'" | tr -d '\r'; }

helper > "$WORK/pme-bench.sh"
$ADB push "$(local_path "$WORK/pme-bench.sh")" "$HELPER" >/dev/null
[ -n "$(on_phone tid)" ] || { echo "automation thread not running" >&2; exit 1; }

case "${1:-idle}" in
    idle)
        SECONDS_IDLE=${2:-600}
        A=$(on_phone schedstat); sleep "$SECONDS_IDLE"; B=$(on_phone schedstat)
        echo "$A $B" | awk -v s="$SECONDS_IDLE" '{printf "idle %ds: cpu %.3f ms (%.2f ms/h), runs %d (%.1f/h)\n", s, ($4-$1)/1e6, ($4-$1)/1e6*3600/s, $6-$3, ($6-$3)*3600/s}'
        ;;
    providers)
        on_phone providers
        ;;
    wifi)
        CYCLES=${2:-20}
        A=$(on_phone schedstat)
        i=0
        while [ "$i" -lt "$CYCLES" ]; do
            $ADB shell svc wifi disable; sleep 8
            $ADB shell svc wifi enable; sleep 20
            i=$((i + 1))
        done
        B=$(on_phone schedstat)
        echo "$A $B" | awk -v c="$CYCLES" '{printf "%d wifi cycles: cpu %.3f ms, %.1f us/cycle, %d runs\n", c, ($4-$1)/1e6, ($4-$1)/1e3/c, $6-$3}'
        ;;
    trace)
        DURATION=${2:-60}
        cat > "$WORK/pme.pbtx" <<EOF
buffers { size_kb: 32768 }
data_sources { config { name: "linux.ftrace" ftrace_config {
    ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_wakeup" ftrace_events: "power/suspend_resume" } } }
data_sources { config { name: "linux.process_stats" } }
duration_ms: $((DURATION * 1000))
EOF
        $ADB push "$(local_path "$WORK/pme.pbtx")" /data/local/tmp/pme.pbtx >/dev/null
        $ADB shell "su -c 'perfetto --txt -c /data/local/tmp/pme.pbtx -o /data/misc/perfetto-traces/pme.pftrace'"
        $ADB pull /data/misc/perfetto-traces/pme.pftrace .
        echo "Open pme.pftrace in ui.perfetto.dev; thread PixelModesEvolv shows slices, wakeups and CPU time."
        ;;
    *)
        echo "usage: $0 idle [s]|providers|wifi [cycles]|trace [s]" >&2
        exit 2
        ;;
esac
