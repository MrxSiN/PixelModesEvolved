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
