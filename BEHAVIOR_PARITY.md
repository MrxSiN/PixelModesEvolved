# Behavior parity

Oracle: the engine at `98ed3ab` plus the working tree as found (diff sha256 `12632f2d…`), kept
verbatim as `LegacyTriggerEngine` in `engine/TriggerEngineParityTest.kt`.

| Feature / edge case | Status | Evidence |
|---|---|---|
| Edge-triggered switching; manual override respected between edges | identical | parity test; `TriggerEngineTest.switches only on edges` |
| Unmet at load leaves Mode alone; met at load switches on without reset | identical | parity test; two `TriggerEngineTest` cases |
| Saved/edited rule applies at once with reset, met or not | identical | parity test; `rule saved after load…` |
| Removed rule that held its Mode on lets it go; order of several | identical (first-decision order) | parity test with up to 4 Modes |
| Stability wait, drop on return, one wait per burst, no wait on load/edit | identical | parity test compares switches and pending-wait count every step |
| Edit cancels the previous version's wait | identical | parity test; `editing a rule drops…` |
| `stop()` drops waits; nothing switches after hand-off | identical | parity test includes `stop()` mid-trace |
| Area hysteresis, memory per Mode, kept across edits for unchanged triggers | identical | parity test (fixes scattered across a 100 m and 30 m edge, with and without accuracy) |
| Unknown signals (null/empty) never meet a trigger | identical | parity test draws null for every nullable slice |
| AND/OR precedence, duplicate triggers, empty rules | identical | parity test generates all three |
| Duplicate Mode ids in storage (last wins, first position) | identical | parity test |
| Failing `ModeController` does not stop other Modes | identical | `TriggerEngineTest.failing controller…` |
| Wi-Fi: SSIDs per network, hidden SSID ignored, loss removes | identical state; fewer duplicate emits | an emit with an unchanged set produced an equal `DeviceState`, which the engine ignored before and after |
| Bluetooth: connect/disconnect/adapter off clears | identical state; fewer duplicate emits | same argument |
| Bluetooth address match is case-insensitive on the stored address | identical | `key` is `address.uppercase()`, outside equality |
| Provider start/stop on demand, retry after failed start | unchanged code | `SignalHubTest` |
| Location stale-fix drop, accuracy, fused/network choice | unchanged code | `FixesTest` |
| Flight detector | changed in v0.0.4 on purpose: cabin pressure starts a flight, one fast fix no longer does, a flight nothing confirms for 20 min ends | `FlightDetectorTest` |
| Motion one-shot sensor, 10 min still timer, unknown at start | unchanged code | `MovementSourceTest` |
| Actions on Mode change, cooldowns, boot never replays | unchanged code | `ModeAutomationTest`, `ActionTest` |
| Rule/action storage format, legacy `match` field | unchanged code | `RuleCodecTest`, `EditorCodecTest` |
| Boot start, hot reload hand-off, feature gating, diagnostics | unchanged code | `HostCompatibilityTest`, `FeatureInstallerTest`, `check-project.sh` |

Log messages are unchanged: switch, wait and failure lines are the same strings.

Not covered by host tests: the Android sources' callback behavior on a real device. Run
`scripts/device.sh check` and `scripts/bench-device.sh providers` after installing.

## Feature 01: system settings

Existing behavior is unchanged: one-shot actions run as before (`ModeActions.at` excludes system
settings), the trigger engine parity test still passes, and stored actions of older builds read
the same (`SystemSettingActionTest`). Claim semantics, with evidence:

| Case | Expected | Host test | On the Pixel 8 Pro |
|---|---|---|---|
| Mode on applies, off restores | yes | `SettingClaimsTest` | all 13 display and sound settings, then Bluetooth, NFC, mobile data, Wi-Fi, hotspot |
| Value already there | no write | `a setting already at the value is not written` | |
| Restore off | value stays | `without restore the value stays` | |
| Changed by hand while claimed | left as changed | two tests (brightness, Wi-Fi) | media volume set to 15 by hand stayed 15 |
| Overlapping Modes | latest wins, then earlier, then baseline | three tests | Transit 30 % and Driving 80 % brightness |
| One setting failing | others unaffected | `one setting failing leaves the others` | Bluetooth refused (before the attribution fix) while NFC and data applied |
| Saved, edited, removed while on | applies at once / gives back | `ActionAutomation` tests | the editor saved Brightness 50 % |
| Mode deleted while on | gives back | `a mode deleted while on gives its settings back` | |
| Hot reload while on | no write, restored at end | `a mode on across an update ...` | auto-rotate held across `device.sh install`, restored at end |
| Boot with Mode on | no write | `a mode on at boot is not applied ...` | not run (reboot not tried) |
| Ended while not running | restored at load if unchanged | two tests | |
| Ringer and ring volume coupling | ringer restored last | `baselines are read before anything is written ...` | Vibrate restored after Ring at 4/7 |
| Mode with Do Not Disturb | Android keeps ringer and ring/notification silent; restored after | | Transit: writes refused by AudioService, state restored |
| Mobile data, data SIM changed | not restored onto another SIM | `mobile data is not restored onto another subscription` | |
