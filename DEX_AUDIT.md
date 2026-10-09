# DEX audit

From `dexdump -d` of the release `classes.dex` and R8's `mapping.txt` (`TriggerEngine` → `j12`,
`TriggerEngine$Compiled` → `i12`; names change per build).

## Event hot path: `onStateChanged` → `changedSignals` → `evaluate` (`j12.b`, 272 code units)

- R8 inlined `Compiled.isMetBy` and `WifiTrigger.isMetBy` into `evaluate`; other triggers are one
  `invoke-interface` each.
- Allocations: none on the no-edge path. `new-instance` appears only in `await` (the timer lambda)
  and in log-message `StringBuilder`s, both on the edge path, which then also calls into
  NotificationManager, a far larger cost.
- Boxing: none. `decision` is an `int`; `flying`/`airplaneMode` are already `Boolean?` in
  `DeviceState` and compared with `equals`, no `valueOf`.
- No iterators, varargs, `Pair`, maps, reflection, JNI or exceptions on the path.
  `getClass()` calls are R8's null checks.
- Logging: none unless a Mode switches or a wait starts. Kept: they are the diagnostics
  `scripts/device.sh logs` reads.

## Config path (`onRulesChanged`)

Allocates maps, arrays and the compiled rules. Runs once per rule save; JSON is parsed there, never
per event.

## Elsewhere in system_server

- Sources allocate one `StateUpdate` lambda and one `DeviceState` copy per real change.
- `AreaTrigger.distanceTo`: haversine on primitives, no allocation. Not rewritten with `x*x`
  in place of `pow(x, 2)`: results could differ in the last bit and move a boundary decision.
- Reflection: `HostResolver` caches every resolved member per process. Reflective calls happen at
  boot (`getContext`), per bonded device at Bluetooth start (`isConnected`), per app launch action
  (`startActivityAsUser`) and per calendar check (`mUserContext`); none per signal event.
- JNI: only DexKit, only inside Settings, only after a known-name lookup fails. None in
  system_server.

## Feature 01: system settings

- Event paths (signals, trigger engine): unchanged.
- Transition path: `SettingClaims` keeps primitive arrays per setting; per transition it allocates
  the snapshot JSON and, on activation, one map entry. Reflection: `Method.invoke` on members
  `HostResolver` resolved once and caches, including failures; never a lookup per call.
- Hotspot uses one `java.lang.reflect.Proxy` per start, for the tethering callback interface,
  which is a system API absent from the SDK.
- No JNI, no shell, no process.
