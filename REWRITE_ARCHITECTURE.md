# Rewrite architecture

Scope of this rewrite, decided by measuring before building (PERFORMANCE.md):

| Component | Decision | Why |
|---|---|---|
| Rule evaluation (`engine/TriggerEngine`) | **Rewritten**: rules compiled on change, dependency-masked evaluation | 2.5–6x less CPU and 94–100 % fewer allocations per event on the host JVM; exact parity proven by differential test |
| Wi-Fi and Bluetooth sources | **Changed**: emit only when the connected set changes | Wi-Fi reports every signal-strength change as a capability change; each used to rebuild a set, a state and run the engine |
| Bluetooth trigger | **Changed**: upper-cased address computed once | Was one `String` allocation per evaluation |
| Location, flight, motion, airplane sources | Kept | Already demand-driven, one-shot or event-based; nothing measurable to remove without changing behavior (POWER_AUDIT.md) |
| `SignalHub`, `ModeAutomation`, actions | Kept | Configuration-time or rare paths |
| Settings hooks, `HostResolver`, DexKit fallback | Kept | Already known-name first, cached per process, DexKit opened only after a miss and closed at once |
| A custom IR / DEX generator / native code | **Not built** | See below |

## Why there is no IR → DEX compiler, and no JNI

A rule set is one rule per Mode: realistically 1–10 rules, 1–4 triggers each. Signals arrive at
human rates (a Wi-Fi join, a fix every 5 minutes, one motion event). After this change one event
costs about 0.1 µs of engine time on a desktop JVM for four rules and allocates nothing in the
engine. A generated-DEX evaluator would save, at best, a few interface calls per event, against a
build-time code generator nobody can review, and a hot reload path that must load generated
classes. That is negative value. JNI would add two transitions to replace a handful of boolean
operations. The compiled form below is the "specialized" representation: arrays, masks and
primitive state, produced by Kotlin → R8 → DEX. R8 already inlines `isMetBy` into the evaluation
loop (DEX_AUDIT.md).

## Pipeline

```
Settings.Secure "mode_evolved_rules" (JSON, unchanged)
   │  ContentObserver → CoalescingTask (one reload per burst)
   ▼
RuleCodec.decode            (configuration change only)
   ▼
List<ModeRule>              persistent model, also what the editor edits
   ▼
TriggerEngine.onRulesChanged → Compiled per rule:
     triggers: Array<Trigger>
     groupEnds: IntArray        `or` groups as end offsets (and binds before or)
     answers:  BooleanArray     previous answer per trigger (area hysteresis)
     signals:  Int              bit per Signal the rule reads
     decision: Int              NONE | OFF | ON, plus first-decision order
     waitMet/timer              the edge being waited out, if any
   read = OR of every rule's signals
   ▼
SignalHub.require(signals)  start what is needed, stop what is not
```

## Event graph

```
source callback (automation HandlerThread)
  → [dedupe in source: unchanged slice → return]
  → SignalHub.publish: state = update(state)   (untouched slices keep their references)
  → TriggerEngine.onStateChanged
       changed = slices differing among `read` (reference check, then equals)
       changed == 0 → store state, done
       for each rule with (rule.signals & changed) != 0: evaluate
  → ModeController.setActive only on a real edge (or after the stability wait)
```

## Provider lifecycle

Unchanged and already minimal: `ModeAutomation.reload` computes the union of signals the stored
rules read, starts newly needed sources before the engine sees new rules (so a saved rule is
judged on live data), then stops sources nothing reads. A source that fails to start stays out of
the running set and is retried on the next rule change. Removing the last rule that reads a
signal stops its provider in the same reload.

## Feature 01: system settings

```
Settings editor (Compose) ── stores ──> Settings.Secure mode_evolved_actions  {"type":"systemSetting",...}
                                              │ ContentObserver (existing), coalesced reload
system_server: ActionAutomation ─ Mode on/off (existing zen etag observer) ─> SettingClaims
                                                                                  │
                                                                SystemSettingsBackend (cached members)
                                                    Settings.System / Secure, AudioManager, DisplayManager,
                                                    UiModeManager, WifiManager, BluetoothAdapter, NfcAdapter,
                                                    TetheringManager, TelephonyManager
```

No new provider, observer, receiver, timer or thread. Claims ride on the Mode transitions the
action automation already detects. The only new I/O is one `Settings.Secure` write of the
ownership snapshot per transition that involves a system setting, and nothing at all for someone
who never adds one. Each setting is a feature (`Feature.SETTING_*`) with its own contract and a
capability probe at start, reported on the compatibility page and passed to the editor.

Control points, checked on `CP3A.261005.005` (each verified on the phone to change the hardware or UI):

| Setting | Read / write | Applied by |
|---|---|---|
| Auto-rotate | `Settings.System accelerometer_rotation` | WindowManager's rotation observer |
| Dark theme | `UiModeManager.get/setNightMode` | UiModeManagerService |
| Brightness | hidden `DisplayManager.get/setBrightness`, slider curve from `Display.getBrightnessInfo` | DisplayPowerController |
| Adaptive brightness | `Settings.System screen_brightness_mode` | DisplayManagerService |
| Extra dim | `Settings.Secure reduce_bright_colors_activated` | ColorDisplayService |
| Screen timeout | `Settings.System screen_off_timeout` | PowerManagerService |
| Always show time and info | `Settings.Secure doze_always_on` | System UI |
| Smooth Display | `Settings.System peak_refresh_rate` (Infinity / 60) | DisplayModeDirector |
| Ringer mode | hidden `AudioManager.get/setRingerModeInternal` (what the volume panel uses) | AudioService |
| Volumes | `AudioManager.setStreamVolume`, hidden `getLastAudibleStreamVolume` for ring and notification | AudioService (safe volume and DND still apply) |
| Wi-Fi | `WifiManager.setWifiEnabled` | Wi-Fi service |
| Bluetooth | `BluetoothAdapter.enable/disable` through an attributed context | Bluetooth service |
| NFC | hidden `NfcAdapter.enable/disable/getAdapterState` | NFC service |
| Hotspot | hidden `TetheringManager.startTethering(int, ...)` with entitlement check, `getWifiApState` | Tethering |
| Mobile data | `TelephonyManager.setDataEnabledForReason(USER)` on the default data subscription | Telephony |

Not built, by design: shell commands, `settings put`, accessibility or UI automation, Tasker.
