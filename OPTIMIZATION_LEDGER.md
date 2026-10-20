# Optimization ledger

Every optimization tried, with what it was measured to do and whether it stayed.

| Change | Measured | Kept |
|---|---|---|
| Rewrite: compiled rules, dependency masks (`TriggerEngine`) | ART 7–16x less CPU per event with rules, 90–100 % fewer bytes (PERFORMANCE.md) | yes |
| Rewrite: Wi-Fi and Bluetooth sources emit only on change | removes the per-callback set, state copy and engine pass for signal-strength callbacks | yes |
| Rewrite: diff only the signals rules read | fixed a zero-rule regression the full diff caused (15 to 65 ns on the JVM) | yes |
| Rewrite: generated DEX / IR compiler, JNI | not built: engine time is under 0.5 µs per event for real rule sets | no |
| Feature 01: claims in dense per-setting arrays, not maps of objects | transition cost 1–4 µs per Mode pair, zero work with no settings | yes |
| Feature 01: skip the second read in settle | would save one service read per setting per transition, but misses settings an earlier write moved (ringer); correctness first | no |
| Feature 01: snapshot JSON per transition | ~4–12 KB per pair, transitions only; cheaper binary form not worth a format | yes, as is |
| v0.0.4: `SecureSettingsStore` keeps the last text and its decoded lists | a Mode page refresh with unchanged rules parses no JSON (it did, twice per refresh: triggers and actions) | yes |
| v0.0.4: `ButtonGroupHooks` resolves its two SettingsLib view ids once | removes two `getIdentifier` name lookups from every bind of an AND/OR row | yes |
| v0.0.4: one shared location stream for area and flight | would save about 2 callbacks every 3 minutes; the fused provider already delivers both at once, and it couples two sources | no |
