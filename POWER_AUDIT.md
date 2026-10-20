# Power audit

Everything runs on one `HandlerThread` ("PixelModesEvolved") in system_server. No alarms, no
wakelocks, no polling loops, no threads per event. Handler delays use uptime, so they never wake
the device from suspend.

| Provider | Started when | Mechanism | Rate | Stopped when |
|---|---|---|---|---|
| Wi-Fi | a rule reads `WIFI` | `NetworkCallback` (Wi-Fi transport, location info) | connectivity events; now forwarded only when an SSID changes | no rule reads it |
| Bluetooth | `BLUETOOTH` | exported receiver: ACL connect/disconnect, adapter state | per broadcast; forwarded only when the set changes | same |
| Location | `LOCATION` | fused (else network), `BALANCED_POWER_ACCURACY` | 5 min, min 60 s; last-known fix used only if ≤ 10 min old | same |
| Flight | `FLIGHT` (a flying trigger) | fused, `HIGH_ACCURACY`; barometer, non-wakeup, batched in the sensor hub FIFO | fixes 3 min, min 30 s, > 2 min old dropped; pressure 1 Hz, delivered at most every 5 min or on the next wake, one reading per 30 s kept | same |
| Movement | `MOVEMENT` | `TYPE_SIGNIFICANT_MOTION`, one-shot wake-up, re-armed per event | per motion; one 10 min still timer, replaced on each motion | same |
| Airplane | `AIRPLANE` | `ContentObserver` on `airplane_mode_on` | per toggle | same |
| Rules | always (feature on) | `ContentObserver` on `mode_evolved_rules`, coalesced | per save | hand-off |
| Mode states (actions) | always (feature on) | `ContentObserver` on `zen_mode_config_etag`, coalesced | per zen config change | hand-off |

## Proof that unneeded work does not run

- `ModeAutomation.reload` → `SignalHub.require(union of rule signals)`: a signal no rule reads
  has no registered callback, receiver, request or sensor. `SignalHubTest` covers start, stop
  and stop-on-last-removal.
- Timers: one per waiting rule (cancelled on return, edit, removal, stop) and one still timer.
  `TriggerEngineParityTest` asserts the pending-timer count equals the old engine's every step.
- Mode API calls: only on a real edge; never "set to what it already is" from a signal (step 1
  of STATE_ENGINE.md). Reset path does at most two calls, only when the Mode is not already on.

## What this rewrite changed

Only CPU after a callback that already woke the thread: unaffected rules are not evaluated,
duplicate Wi-Fi/Bluetooth callbacks stop at the source. No provider, rate or accuracy changed;
no new wakeup source was added.

## Left as is, deliberately

- Location and flight together hold two requests (balanced and high accuracy). Merging them
  would change the fix rate and accuracy either rule sees.
- The Wi-Fi callback itself still fires on signal-strength changes; `NetworkCallback` cannot
  filter them out, and the delivery happens whether the work after it is done or not.

## On-device confirmation (Pixel 8 Pro, CP3A.261005.005, rule `wifi OR wifi OR area`)

`scripts/bench-device.sh providers` on baseline and rewrite: exactly one location request,
`fused BALANCED 5 min, min 1 min`, then attributed `PixelModesEvolved` (since v0.0.4 `GeofencingService`, a tag
the `android` package declares, so AppOps stops logging an error on every delivery); old registration removed at
hand-off before the new one was added; no significant-motion listener from system_server (no
rule reads movement); 0 alarms; 0 wakelocks. Same on both builds. Idle 15 min: 21 runs / 12.1 ms
(baseline) against 20 runs / 11.9 ms (rewrite), PERFORMANCE.md.

## Feature 01: system settings

- No provider, observer, receiver, sensor, location request, alarm, wakelock, thread or polling
  was added. Claims are driven only by the Mode transitions the action automation already
  observes through `zen_mode_config_etag`.
- Restoration is transition-driven: a value changed by hand is noticed only by comparing at the
  next transition, never by watching.
- Zero system settings configured: no snapshot write, no backend call, no allocation per
  transition (benchmark above). The one-time capability probe at start resolves members and asks
  a few services whether hardware exists; it does nothing afterwards.
- A setting a Mode turns on can itself cost power (hotspot, Bluetooth, brightness, Smooth Display):
  that is the person's choice, and the module turns it back when the Mode ends.

## v0.0.4: flight detection

- The flying trigger adds the barometer: `TYPE_PRESSURE`, non-wakeup, 1 Hz (its slowest rate),
  `maxReportLatency` 5 min. The ICP20100 on a Pixel 8 Pro has a 3,000-event FIFO, so readings
  queue in the sensor hub and arrive whenever the phone is awake anyway, such as for the flight's
  own 3-minute location request. It never wakes the phone. One reading per 30 s is kept and the
  rest dropped at the listener; the state is published only when the answer changes.
- Registered only while a rule reads `FLIGHT`, released with the rest of the source; a hot reload
  was seen to unregister before registering again (`dumpsys sensorservice`).
- Nothing changed for a phone with no flying trigger.
