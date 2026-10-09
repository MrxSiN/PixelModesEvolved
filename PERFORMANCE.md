# Performance

## Revisions

- Baseline: `98ed3ab` + uncommitted working tree as found on 2026-10-09 (`git diff HEAD | sha256sum`
  = `12632f2d3f3df07f11c996fcf2c2a6d1b7e465786690c719358fdf26cd5bc5dc`).
- Rewrite: baseline + the changes listed in REWRITE_ARCHITECTURE.md.

## Environment

- Host: Windows 11, Android Studio JBR (OpenJDK 25.0.3) for Gradle and unit tests.
- Device: Pixel 8 Pro (husky), `google/husky/husky:17/CP3A.261005.005/16271449:user/release-keys`,
  rooted, Vector; connected with adb over Wi-Fi (so the Wi-Fi toggle benchmark was not run: it
  would cut the connection). Stored rules on the device: one Mode, `wifi OR wifi OR area(50 m)`.

## Engine cost per state change on the phone (ART, `dalvikvm64`)

Same trace as the host benchmark below, plus the phone's own rule. Harness:
`ArtBench.java` (session scratchpad), the debug-compiled main classes and the legacy engine from
the test classes, dexed with `d8 --release` (not R8, so the shipped code is if anything faster).
Allocation counts from `android.os.Debug` thread alloc counters. 300 warm-up passes, 15 runs of
50 passes × 1,024 events; median ns/event (min–max). Three runs, two orders:

| Config | Legacy ns (3 runs) | Compiled ns (3 runs) | Legacy obj / B | Compiled obj / B |
|---|---|---|---|---|
| zero rules | 157, 64, 147 | 42, 48, 38 | 1 / 32 | 0 / 0 |
| one Wi-Fi rule | 1,657, 1,519, 1,630 | 176, 202, 240 | 18 / 480 | 1 / 48 |
| four mixed rules | 4,277, 6,113, 7,465 | 275, 389, 443 | 77 / 2,073 | 2 / 75 |
| phone's rule (2 Wi-Fi or area) | 1,756, 2,223, 2,198 | 232, 262, 267 | 28 / 808 | 1 / 41 |
| 32 rules | 54,410, 65,561, 60,663 | 5,276, 3,961, 4,234 | 801 / 22,304 | 0 / 0 |

On ART the gap is larger than on the desktop JVM: 7–16x less CPU per event with rules, and the
zero-rule case is 1.3–4x cheaper too. Bytes allocated drop 90–100 % (objects 94–100 %). The one
remaining object on the Wi-Fi rows is `AbstractSet.equals`' iterator when the Wi-Fi set really
changed in the trace; on the device the hub keeps an untouched slice's reference, so other slices
never reach `equals`.

## Commands

```sh
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew test assembleRelease
sh scripts/check-project.sh
BENCH=1 BENCH_ORDER=legacy,compiled ./gradlew testDebugUnitTest --tests '*Benchmark*' --rerun -i
BENCH=1 BENCH_ORDER=compiled,legacy ./gradlew testDebugUnitTest --tests '*Benchmark*' --rerun -i
```

## Engine cost per state change (host JVM proxy, `TriggerEngineBenchmark`)

1,024-event trace alternating Wi-Fi changes and location fixes along an area edge; 200 warm-up
passes, then 15 runs of 50 passes; median ns/event (min–max), bytes allocated per event.

Order legacy → compiled:

| Config | Legacy ns | Compiled ns | Legacy B | Compiled B |
|---|---|---|---|---|
| zero rules | 17 (15–28) | 65 (62–83) | 0 | 0 |
| one Wi-Fi rule | 256 (133–830) | 85 (79–123) | 288 | 16 |
| four mixed rules | 601 (491–912) | 98 (94–118) | 1,608 | 25 |
| 32 rules | 9,144 (8,544–9,466) | 2,328 (2,288–2,388) | 17,664 | 0 |

Order compiled → legacy:

| Config | Compiled ns | Legacy ns | Compiled B | Legacy B |
|---|---|---|---|---|
| zero rules | 9 (9–10) | 72 (72–74) | 0 | 0 |
| one Wi-Fi rule | 80 (78–171) | 161 (122–459) | 16 | 288 |
| four mixed rules | 103 (96–110) | 620 (491–886) | 25 | 1,608 |
| 32 rules | 2,386 (2,254–2,415) | 8,734 (8,488–9,410) | 0 | 17,152 |

Reading: the zero-rule row flips with run order, so it is a JIT call-site artifact (whichever
engine runs first gets a monomorphic call site), not a difference between the engines. With rules,
compiled is 2.5–6x faster and allocates 94–100 % less, in both orders. The remaining 16–25 B/event
is `AbstractSet.equals`' iterator when a Wi-Fi set really changed; on device the hub keeps an
untouched slice's reference, so it is skipped for every other slice.

Percentages (mean of the two orders' medians): one rule −60 %, four rules −84 %, 32 rules −74 % CPU;
allocation −94 %, −98 %, −100 %.

## Source-level reduction (not timed)

Each Wi-Fi capability callback that does not change an SSID (signal strength, link speed) used to
allocate a set, a lambda and a `DeviceState`, and run the engine's equality check. Now it does a
`HashMap.put` and returns. Same for repeated Bluetooth ACL broadcasts and adapter-state broadcasts
with nothing connected.

## Artifact

| | Baseline | Rewrite | Δ |
|---|---|---|---|
| APK | 8,146,227 B | 8,146,943 B | +716 B (+0.009 %) |
| classes.dex | 3,048,488 B | 3,049,196 B | +708 B (+0.023 %) |

The DEX is almost entirely the Compose/MapLibre editor; the system_server code is a small part
of it. The growth is the compiled-rule class and the mask code.

## Tests

Baseline 129 tests, 0 failures. Rewrite 131 tests (parity test added, benchmark skipped without
`BENCH=1`), 0 failures. `check-project.sh` passes on both.

## Automation thread in system_server, idle (Pixel 8 Pro)

`scripts/bench-device.sh idle 900`: `/proc/<system_server>/task/<tid>/schedstat` of the
automation thread before and after 15 minutes, screen off, phone on the desk, same stored rule.
Builds swapped in place by hot reload.

| Build | Window | CPU on thread | Times run |
|---|---|---|---|
| Baseline | 20:06:43 + 900 s | 12.117 ms (48.5 ms/h) | 21 (84/h) |
| Rewrite | 20:22:21 + 900 s | 11.929 ms (47.7 ms/h) | 20 (80/h) |

Parity, within noise (one sample each). Idle runs are location fixes, zen-config notices and
Wi-Fi callbacks; about 0.6 ms per run is spent in framework delivery, against well under 1 µs of
engine time, so the engine change cannot show up here. It shows up per event (ART table above).
Run counts did not rise: the rewrite added no work.

## Not measured

Settings page-open latency and hook-install time (Settings code is unchanged), GC counts in
system_server, the Wi-Fi toggle benchmark (adb runs over Wi-Fi on this phone), Perfetto
wakeup attribution. Commands:
```sh
scripts/bench-device.sh providers
scripts/bench-device.sh idle 600
scripts/bench-device.sh wifi 20
scripts/bench-device.sh trace 60
```

## Feature 01: system settings

Host JVM, `SettingClaimsBenchmark` (`BENCH=1`), backend doing nothing, so this is the module's own
time around the framework calls. One on/off pair, median of 15 runs of 5,000:

| Case | ns per pair | bytes per pair | backend calls per pair |
|---|---|---|---|
| zero settings | 33 (30–36) | 0 | 0 |
| one switch | 1,769 (1,248–3,531) | 3,768 | 3 |
| one percent | 1,286 (1,226–2,249) | 3,856 | 5 |
| five mixed | 3,667 (3,502–4,224) | 11,680 | 21 |
| two overlapping Modes, same five | 11,282 (10,910–11,639) | 35,368 | 48 |

Most of the bytes are the ownership snapshot (JSON) stored on each transition. This runs once per
Mode transition that holds a setting, against framework calls that each cross into a service
(every write here is a Binder or settings-provider call, tens of microseconds and more), so it is
not optimised further. Zero configured settings: 33 ns and no allocation per pair, no storage
write, no backend call. The signal and trigger paths are untouched by this feature.

Backend calls per setting and transition: one read for the baseline and one in settle (the second
catches settings an earlier write moved, such as the ringer), at most one write, and one read
back for brightness and volumes, which Android may clamp.

On the phone (Pixel 8 Pro, `CP3A.261005.005`): all 13 display and sound settings applied within
0.62 s of the Mode switching on (log timestamps 21:06:47.763 to 48.379), dominated by the
services themselves (dark theme ~100 ms, ringer ~105 ms, ring volume ~185 ms). Not measured: the
Settings action-list open time; the editor is the existing Compose activity with one more list.

Artifact after Feature 01: APK 8,203,939 B (+56,996 B against the rewrite's 8,146,943 B),
classes.dex 3,083,028 B (+33,832 B), mostly the editor screen and the 18-setting catalogue.
Tests: 164, 0 failures, 2 skipped (the two `BENCH=1` benchmarks).
