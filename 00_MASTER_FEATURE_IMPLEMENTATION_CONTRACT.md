# MASTER FEATURE IMPLEMENTATION CONTRACT

## Read this before every feature prompt

You are an autonomous Android platform, system-performance, UI-integration and compiler engineer working inside `MrxSiN/PixelModesEvolved`.

A major rewrite has already been completed or is being completed under the repository's `CLAUDE.md` and rewrite specification. **Those documents are higher-authority constraints than any convenience suggested by a feature prompt.** Read them in full before editing code.

Your job for a feature task is to **implement the feature completely**, not merely design it. Inspect the rewritten repository first. Do not assume legacy package names, file paths, class names, serialized formats, hook points, or UI composition survived the rewrite.

---

# 1. Global objective

The project extends Android 17 Pixel Modes while performing the minimum possible work inside Android system processes.

Order of priority:

1. correctness and compatibility;
2. minimum idle power/wakeup cost;
3. minimum `system_server` and Settings overhead;
4. no work when the feature is not configured;
5. minimum allocations, messages, framework calls and GC;
6. low event-to-decision latency;
7. low memory/code footprint;
8. native Pixel Settings UX;
9. implementation/source readability is secondary.

Do not optimize a local nanosecond while adding a provider, wakeup, process start, observer, reflection lookup or framework call.

---

# 2. Runtime-domain contract

## Region A — injected/resident host runtime

Anything injected into or resident in `system_server`, Settings, framework/service host processes, libxposed callbacks, observers, providers, receivers, Mode/Zen integration or the automation engine must remain:

**Java / machine-generated Java / reproducible generated DEX → R8/D8 → optimized DEX → ART**

Forbidden in Region A without explicit user authorization:

- Kotlin runtime implementation;
- C/C++/Rust/Zig/Go;
- JNI/native helpers;
- scripting/WebAssembly;
- another runtime.

Normal event paths should aim for:

- no runtime configuration parsing;
- no reflection lookup;
- no temporary collections;
- no iterators;
- no boxing/varargs;
- no temporary strings;
- no expected-path exceptions;
- no release logging;
- ideally zero allocation;
- evaluation only of rules affected by the changed signal unless measured tiny-rule scans win.

## Region B — cold module/editor UI

Preserve the existing editor UI stack. Compose is allowed here. Do not rewrite UI to Java merely for language purity and do not introduce another UI framework for convenience.

Evaluate editor changes separately for:

- activity/page startup;
- interaction latency;
- allocations/memory;
- APK/DEX size;
- accessibility and UX correctness.

Heavy media/UI work must not be moved into `system_server` simply to avoid a cold app process.

---

# 3. Existing behavior is an oracle

Before changing behavior:

1. record the current commit;
2. build the existing rewritten release;
3. run tests/check scripts;
4. inventory the exact trigger/action persistence and runtime flow touched by the feature;
5. capture relevant traces/tests;
6. preserve all unrelated behavior.

Existing project behavior that must not regress includes, when still present after the rewrite:

- Wi-Fi trigger;
- Bluetooth trigger;
- location trigger including accuracy/staleness/hysteresis behavior;
- movement triggers including unknown state;
- airplane-mode trigger;
- AND/OR trigger semantics;
- wait/debounce semantics;
- manual Mode override semantics;
- Mode actions and action cooldown semantics;
- calendar event filter;
- schedule removal;
- extra Mode icons in the icon picker;
- hot reload/update;
- boot semantics;
- compatibility diagnostics;
- feature isolation/safe failure.

Do not silently “clean up” edge semantics.

---

# 4. Configuration is not execution

Persistent configuration is a storage/upgrade format.

On configuration change:

- parse once;
- validate once;
- canonicalize;
- migrate if needed;
- assign dense IDs where useful;
- compile to primitive state/tables;
- build reverse dependencies where beneficial;
- compute provider dependency counts;
- precompute constants;
- atomically publish the compiled graph/state.

On an ordinary event, do not rebuild JSON/XML objects, `List`, `Set`, `Map`, sequences, lambdas, strings or generic rule trees.

Any new trigger must plug into the same compiled signal/dependency graph. Any new action must plug into the existing compiled action dispatch/Mode transition flow. **Do not build a second automation engine.**

---

# 5. Provider lifecycle

Every new event source/provider is dependency-counted.

Requirements:

- do not register it until at least one enabled rule needs it;
- unregister/stop it promptly after the last dependent enabled rule disappears;
- share one provider across all dependent rules;
- do not poll when a callback/broadcast/observer exists;
- do not wake unrelated rules;
- do not keep a module process alive merely to wait for events;
- do not create one timer/observer per rule when one aggregated scheduler/provider can preserve semantics.

Zero configured rules for a feature must mean effectively zero feature-specific ongoing work.

---

# 6. Tri-state and event semantics

State triggers should represent `UNKNOWN`, `FALSE`, `TRUE` when the platform cannot immediately know a correct value.

Do not reinterpret unknown as false if that would cause a Mode transition that the device does not know is justified.

Event-like triggers must not be awkwardly forced into permanent booleans. If a feature needs a pulse/latch/deadline model, add it deliberately to the compiled engine and document it. Aggregate timers/deadlines rather than creating per-rule wakeups.

---

# 7. Action ownership and reversible effects

Features that change a device setting must use a shared resource/action coordinator rather than bespoke restore logic per action.

For a reversible setting resource:

1. identify the setting with a dense/stable resource key;
2. on the first PME claim, capture the current baseline;
3. track active claims from Modes;
4. resolve competing claims deterministically;
5. write only when the desired value differs from the current/last-applied value;
6. when a winning claim disappears, apply the next winner if required;
7. when the final claim disappears, restore the baseline **only if the current real value still equals the value PME last wrote**;
8. if the user or another subsystem changed the value, do not clobber that external change just to restore an old snapshot.

Default conflict policy for new reversible actions, unless the repository already defines another proven behavior:

- most recently activated still-active Mode claim wins;
- removing that claim reveals the next active claim;
- final removal conditionally restores the baseline as above.

Persist only what must survive process/system lifecycle. Do not persist transient hot-state objects.

Actions must execute only on genuine Mode transitions according to the project's action semantics. Boot/load/edit must not replay stale historical transitions.

---

# 8. Native Pixel Settings integration

The visual target is Pixel Settings → Modes on Android 17 / API 37.

## Injected Settings surfaces

If the feature appears on a stock Mode page:

- insert it into the existing Mode information hierarchy where a Google Settings engineer would put it;
- use Settings' own preference classes/components/resolved resources through the project's compatibility layer;
- reuse host typography, spacing, icon sizing, ripple, pressed states, dividers, summaries and navigation;
- avoid custom-drawn “lookalike” rows;
- let host list scrolling and transitions behave naturally;
- use host strings/resources when semantically identical and stable; otherwise supply localized module strings;
- do not block Settings startup with expensive package scans or compatibility discovery.

## Module editor surfaces

Use the existing Compose/editor technology and the current Material 3 Expressive direction.

Requirements:

- dynamic color from the device/wallpaper where the project already does this;
- `MaterialTheme.motionScheme` or the project's current equivalent;
- use Material components and motion rather than hand-tuned arbitrary easing/durations;
- edge-to-edge;
- proper system bar/icon contrast;
- predictive back;
- standard search behavior;
- standard check/radio/switch/slider components;
- responsive layouts and font scaling;
- TalkBack semantics;
- touch targets;
- reduced-motion/system animator-scale respect;
- no gratuitous hero animation for utilitarian settings.

Use expressive motion for meaningful state/navigation changes; use standard/utilitarian motion for recurring controls.

Do not upgrade Compose/Material solely to chase a new API unless the project benefits and compatibility/build impact is measured.

---

# 9. Current Android visual baseline

Treat Android 17 / API 37 Pixel behavior as the current reference, not Android 12-era screenshots.

Material 3 Expressive principles to preserve:

- wallpaper-derived dynamic color;
- stronger hierarchy and typography;
- responsive expressive components;
- natural/spring-like motion where appropriate;
- clear glanceability;
- shape/state feedback that remains functional rather than decorative.

Predictive back must be supported on editor/activity navigation and must not be hidden by custom transitions.

---

# 10. Compatibility strategy

Never hard-code a guessed hidden framework contract and assume it survives.

For every host/internal dependency:

1. investigate the actual Android 17 QPR1 target build;
2. create a known class/member/signature fast path;
3. validate the resolved contract;
4. cache the result safely;
5. run structural/signature fallback only after fast-path failure;
6. keep fallback discovery lazy and cold;
7. report the feature as unavailable if resolution cannot be proven safe.

A missing contract disables **that feature**, not the module.

Do not repeatedly scan DEX or resolve reflection on an event path.

---

# 11. Security and privilege

Remember that work executed from `system_server` carries system authority.

For every feature:

- minimize externally reachable surfaces;
- validate persisted data again at execution;
- validate package/component identities;
- enforce current-user/profile boundaries;
- reject malicious/invalid URIs, components and values;
- never trust an editor-only validation check;
- avoid generic implicit broadcasts from system uid;
- do not log sensitive app, notification, message, location or contact content;
- do not let a malformed rule crash Settings or `system_server`.

If a feature expands module scope into another privileged process, document the reason and cost. Do not add process scope casually.

---

# 12. Multi-user and profile behavior

Investigate and explicitly define:

- current Android user;
- managed/work profile;
- user switch;
- profile quiet mode;
- package installed in only one profile;
- Settings editing a Mode for the current user;
- boot-before-unlock vs credential-encrypted data.

Do not accidentally apply a personal Mode's action to another user/profile.

---

# 13. Persistence and migration

Do not break existing stored rules/actions.

If the feature extends the persistent format:

- maintain backward parsing;
- use a versioned extension or compatible new field/type;
- write migration tests;
- document it in `MIGRATION.md`;
- do not reinterpret old values;
- ensure downgrade/failure behavior is understood where relevant.

A corrupt new record must be isolated and ignored/reported rather than poisoning all automation.

---

# 14. Failure containment and diagnostics

Extend the project's compatibility/diagnostics surface with:

- feature name;
- active/available/unavailable status;
- resolved contracts;
- fast-path vs fallback resolution;
- failure reason;
- provider state when useful;
- sanitized counters/state where safe.

No sensitive configured values in generic bug reports unless the user explicitly chooses to include them.

Feature failure must degrade gracefully and preserve stock Android behavior.

---

# 15. Testing

Every feature requires tests at these levels where applicable.

## Pure engine/model tests

- unknown/false/true;
- initial load;
- edits;
- removal;
- AND/OR combinations;
- waits/cancelled waits;
- Mode manual changes;
- concurrent Mode claims;
- provider start/stop;
- user switch;
- hot reload;
- boot;
- malformed config.

## Differential/regression tests

Existing features must produce identical observable results for identical traces.

## UI tests/screenshots

Exercise:

- light/dark;
- multiple dynamic color palettes where practical;
- 1.0x and large font;
- gesture navigation/predictive back;
- long app/device names;
- empty/loading/error states;
- RTL when text/layout warrants;
- accessibility labels;
- animation scale 0 and normal.

## Device tests

On supported hardware, test actual platform behavior, not only mocks.

---

# 16. Performance/power acceptance

Add a feature-specific section to `PERFORMANCE.md`, `POWER_AUDIT.md` and `OPTIMIZATION_LEDGER.md`.

Measure as applicable:

- provider registration cost;
- provider active duration;
- idle work with zero rules;
- idle work with configured-but-unchanged state;
- event → signal update;
- signal update → affected rules complete;
- Mode transition → action execution;
- allocations/event;
- Handler/Looper messages;
- framework calls/event;
- memory;
- wakeups/alarms;
- Settings page-open cost;
- editor open/search/select cost.

Use clean release artifacts for final claims.

Never claim “zero overhead”; report measurable incremental cost and whether it is within noise.

---

# 17. Documentation deliverables

Update relevant repository documents, not only code:

- `README.md`;
- `RELEASE_NOTES.md`;
- `REWRITE_ARCHITECTURE.md`;
- `STATE_ENGINE.md`;
- `BEHAVIOR_PARITY.md`;
- `PERFORMANCE.md`;
- `POWER_AUDIT.md`;
- `DEX_AUDIT.md`;
- `MIGRATION.md`;
- `OPTIMIZATION_LEDGER.md`;
- compatibility matrix / device scripts if applicable.

Document user-facing semantics and difficult edge cases.

---

# 18. Completion behavior

Do not stop after writing a design document.

At the end:

1. implementation complete;
2. format/lint/checks complete;
3. unit/model/UI tests complete;
4. release APK builds;
5. compatibility diagnostics updated;
6. device tests run if hardware is connected;
7. benchmark scripts/results included;
8. no unsupported performance claim;
9. losing optimizations reverted;
10. provide a concise final implementation report listing files changed, behavior added, tests, measurements, known limitations and any unmeasured device-only item.

If no physical device is available, finish everything else and provide ready-to-run ADB/Perfetto/device scripts. Mark runtime results **unmeasured**, not “expected fast”.
