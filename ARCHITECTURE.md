# Architecture

Pixel Modes Evolved follows the same principles as Pixel Launcher Evolved and Pixel Lock Screen
Evolved. The shape is the same; the source trees are not, because Modes runs in two hosts with
different jobs.

```
thin host router            ModeEvolvedModule
  -> explicit feature       core.Feature + core.FeatureHooks, one class per feature
  -> host integration       page/ (Settings), system/ + signal/ (system_server)
       and resolution       dex/HostResolver, dex/DexClassFinder, dex/NamedClassFinder
  -> pure domain logic      trigger/ engine/ rule/ action/ calendar/EventFilter
  -> per-feature gating     compat.FeatureContracts, checked by core.FeatureInstaller
  -> diagnostics/recovery   diagnostics/, store/DiagnosticsStore, ModuleUpdateRestarter, hot reload
  -> CI                     scripts/check-project.sh, HostCompatibilityTest on recorded builds
```

## Process boundaries

These are the part of Modes that is its own, and they stay as they are:

| Process | Owns | Never does |
|---|---|---|
| `com.android.settings` | The UI on each Mode's page, and storing rules and actions (`Settings.Secure`) | Decide when a Mode switches |
| This module's app | The editor and the compatibility page Settings opens | Store anything, or hook anything |
| `system_server` | The automation: signals, rule evaluation, switching Modes, running actions | Draw UI |

`ModeEvolvedModule` only routes: system_server to `SystemServerPart`, Settings to `SettingsPart`.
Each part is the composition root of its host and installs that host's features.

## Compatibility contracts

Every private class, method, field and constructor a feature reaches for is declared once, as a
value in `compat/SettingsApis.kt` or `compat/SystemServerApis.kt`. Each declares:

- its **host process**, through its class (`Host.SETTINGS` or `Host.SYSTEM_SERVER`);
- its **known signature**: class name, member name and parameter types;
- for a method, **scope**: `HOOK` (declared by the owner itself, with a body) or `CALL` (the owner
  or any class above it);
- any legitimate **fallback signatures**, tried in order;
- for a class, a **structural locator**, describing what may stand in for it when renamed:
  a `DexSearch` in its own package by method shape, or the `Ancestor` of a class that declares a
  given method.

`compat/FeatureContracts.kt` attributes each of them to a **stable feature ID** (`core.Feature`,
whose `id` is what storage and reports use), as **required** or **optional** for that feature. A
feature is installed only when all its required members resolve; an optional member costs only
what it adds.

Contracts describe *what* a feature needs. *How* a class is found is the resolver's policy, which is
Modes' own and stays:

1. The known name first. A matching build costs one `loadClass`.
2. Only when that fails, and only for a class whose contract has a structural locator, a search:
   DexKit restricted to the class's own package and method shape, or the class's line of ancestors.
   system_server never searches (it would parse every boot jar at boot).
3. A search that finds more than one candidate finds nothing. Hooking the wrong class inside
   Settings is worse than losing the feature.

Hook code never names a private member as a string or looks one up by reflection. It asks
`HostResolver` for a contract value, which applies the scope and fallbacks, records the outcome
for the compatibility page, and caches it. `scripts/check-project.sh` holds that line.

## Host fixtures and runtime diagnostics

They answer different questions, and both are kept.

**Fixtures** stop this project's own regressions before a release. For each verified Pixel build,
`app/src/test/resources/hosts/<Build.ID>/` holds a description of each host, written by
`scripts/capture-host-fixture.sh` from a phone on that build: for every class a contract names,
what it extends, only the members whose names some contract uses, and what each structural locator
finds. Names only; no APK, dex or code of Google's is stored. `HostCompatibilityTest` checks every
feature's contract against every recorded build and reports each break as
`FEATURE [host] requires Member: what happened`. It also checks that every structural fallback
would find exactly the class it stands in for: on `CP3A.260905.009` this caught two DexKit shapes
that matched two and three classes, so the fallback could never have worked.

To add a build: put the phone on it, run `scripts/capture-host-fixture.sh`, check the features by
hand, and add the build to `compat/VerifiedBuilds.kt`.

**Runtime diagnostics** identify a build nobody has recorded. On the phone, each feature is gated on
its own contract as it installs, the outcome of every member is recorded (by name, by structural
search, by fallback, missing), and the compatibility page at the bottom of the Modes list shows it
per feature with the reason. `scripts/device.sh health` prints the same.

## Concurrency

One rule, everywhere:

- **An event source runs only while something needs it.** `SignalHub` starts a signal source when
  a rule needs it and stops it when none does; Settings observers are bound to the page lifecycle.
- **A change notification means "look again", not "one more job".** A burst of rule writes or Mode
  state changes becomes one look at the latest state (`core.CoalescingTask`), never a queue of
  reloads.
- **Expensive latest-state work replaces stale requests.** Opening an editor reads device facts off
  the main thread; a newer tap supersedes one still loading (`core.LatestOnly`), so only the newest
  editor opens.

Everything in system_server runs on the one automation thread, so the domain logic needs no locks.

## Threat model

**Assets.** Rules and actions stored in `Settings.Secure`, which system_server carries out with
system privileges; the Modes themselves; broadcasts system_server sends, which receivers trust as
the system's.

**Boundary 1: the exported editor and compatibility page.** Both are exported because Settings,
another app, starts them. Both refuse, before reading anything, a caller the system did not record
as `com.android.settings` (`security.SettingsCaller`): the calling package, set only for
`startActivityForResult`, or the launching package, revealed only when the launcher opts in with
`ActivityOptions.setShareIdentityEnabled(true)`, which `page.EditorOpener` does for every launch.
Neither can be forged by the sender. What the intent claims about its sender is never used: the
compatibility page used to trust `Activity.getReferrer()`, which `am start --es
android.intent.extra.REFERRER_NAME android-app://com.android.settings` spoofs; it was verified
open on `CP3A.260905.009` and is closed now.

What a rejected caller could have had: the editor shows input and returns an item to whoever
started it for a result, and the page shows a report handed to it. Neither holds a secret, and
neither stores anything; Settings stores results only for its own request codes, from the explicit
component it started.

**Why not a manifest permission.** A permission on the activities would be a second lock only if it
is held by Settings on every supported build and grantable to nothing else. The system uid passes
every permission check, so any signature permission would admit Settings; but it would also admit
every other privileged holder, and whether it is present is not something the fixtures record yet.
Until a permission is verified across every recorded build, the caller check above is the boundary.

**Boundary 2: stored rules and actions.** Anything with `WRITE_SECURE_SETTINGS` (adb, some system
apps) can write them, bypassing the editor. So validation that matters is repeated where the
privilege is used: a broadcast name must pass `IntentActions` (no platform-owned names), and the
only send path takes a `SendableAction`, which only that check can make. Launching an app uses its
own launcher intent; Tasker gets a fixed action. Undecodable entries are dropped.

**Boundary 3: hooks inside Settings and system_server.** Every hook is protective, gated on its
feature's contract, and fails alone; a missing member costs its feature, never the host.

## Resource IDs

For a moment after an update, Settings runs the old build's hook code with the new build's
resources. `app/resource-ids.txt` pins every ID (`--stable-ids`), is sorted so it is the same file
on every machine, and must hold every string and drawable before the build runs; CI then checks the
release build left it unchanged.
