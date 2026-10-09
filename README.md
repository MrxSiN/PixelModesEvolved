<div align="center">

<img src="docs/icon.svg" width="120" alt="Pixel Modes Evolved">

# Pixel Modes Evolved

**The triggers and actions Android 17 Modes do not ship — put on each Mode's own page in Settings.**

Settings → Modes → *a Mode* → **Mode settings**, under "Set a schedule".

<br>

[![Release](https://img.shields.io/github/v/release/MrxSiN/PixelModesEvolved?include_prereleases&color=5B3DF5&label=release&style=for-the-badge)](https://github.com/MrxSiN/PixelModesEvolved/releases)
[![Downloads](https://img.shields.io/github/downloads/MrxSiN/PixelModesEvolved/total?color=3DDC84&logo=android&logoColor=fff&style=for-the-badge)](https://github.com/MrxSiN/PixelModesEvolved/releases)
[![Android](https://img.shields.io/badge/Android-17%20QPR1-3DDC84?logo=android&logoColor=fff&style=for-the-badge)](https://developer.android.com)
[![Status](https://img.shields.io/badge/status-alpha-E8A33D?style=for-the-badge)](#status)

</div>

---

> [!WARNING]
> **Alpha.** This is an early release. It hooks Settings and system_server. See the
> [compatibility matrix](#compatibility) for what has actually been tested on what.
> Expect rough edges, and keep a way to recover your phone.

## Why it works this way

Android 17 Modes can follow a time or a calendar, and nothing else. This module adds the
missing triggers and actions without a second settings app: every row is drawn on the Mode's
own page, with Settings' own preference classes, so it looks, animates and scrolls like the rest
of that page. The decisions run inside system_server, where a Mode can be switched without any
app holding a special permission.

|  | |
|---|---|
| 🧭 **Where you already are** | Triggers and actions sit on each Mode's page, next to its schedule. |
| 🔋 **Only what is needed runs** | A Wi-Fi-only setup never asks for location or motion. Each input starts when a rule needs it and stops when none does. |
| ✋ **You stay in charge** | A Mode is switched only when its triggers change between met and not met. Turn a Mode off by hand and it stays off until the triggers change again. |
| 🔁 **No reboot to update** | system_server hot reloads a new build; Settings restarts itself once it is out of sight. |
| 🧩 **Contained** | Every hook is protective. A failing trigger, action or page costs its own feature, never Settings or the system. |

---

## Features

<details open>
<summary><b>⚡ Triggers</b></summary>
<br>

Tap **Add a trigger** on a Mode's page and choose what turns the Mode on. It turns off again
when the triggers stop being met.

| Trigger | Met while | Watched with |
|---|---|---|
| **Wi-Fi network** | connected to a network with a given name | a `ConnectivityManager` network callback |
| **Bluetooth device** | a paired device is connected | connect and disconnect broadcasts |
| **Location** | inside a circle around a place | the fused provider, balanced power, about every 5 minutes |
| **Movement: on the move** | the phone is moving | the significant motion sensor, one-shot and low power |
| **Movement: left still** | the phone has not moved for 10 minutes, counted from when the rule starts | the same sensor |
| **Movement: flying** | on board an aircraft in flight | high-accuracy fixes every 3 minutes: speed and altitude |
| **Airplane mode** | airplane mode is on, or off | the `airplane_mode_on` setting |

Triggers are joined in order by **AND** or **OR**, with a connected button group between each
pair. As in ordinary logic, AND binds before OR: `Home Wi-Fi AND left still OR Car` means
`(Home Wi-Fi AND left still) OR Car`. A new trigger joins with AND.

Under the triggers sits **Wait before switching**: how long they must keep saying the same thing
before the Mode is actually switched. Wi-Fi roaming between two access points, a headset
reconnecting and a position fix wandering along a boundary all produce real edges, and without a
wait each of them switches the Mode and runs its actions. Thirty seconds covers most of it.
Saving a trigger still applies at once — that is you, not a noisy signal.

<table>
<tr>
<td align="center"><img src="docs/screenshots/mode-page.png" width="250"><br><sub>Triggers and actions on a Mode's page</sub></td>
<td align="center"><img src="docs/screenshots/trigger-picker.png" width="250"><br><sub>Choosing a trigger</sub></td>
<td align="center"><img src="docs/screenshots/trigger-wifi.png" width="250"><br><sub>Wi-Fi: the connected network, saved ones, or any name</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/trigger-bluetooth.png" width="250"><br><sub>Bluetooth: paired devices, searchable</sub></td>
<td align="center"><img src="docs/screenshots/trigger-location.png" width="250"><br><sub>Location: search a place, pan the pin, set the range</sub></td>
<td align="center"><img src="docs/screenshots/trigger-movement.png" width="250"><br><sub>Movement: on the move, left still or flying</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/trigger-airplane.png" width="250"><br><sub>Airplane mode: on or off</sub></td>
<td></td>
<td></td>
</tr>
</table>

The editor is built with Compose and Material 3 Expressive, in wallpaper colors, and all of its
motion follows `MaterialTheme.motionScheme`.

- **Wi-Fi** lists the network the phone is on and every saved network. The search field filters
  the list, or names a network the phone has never joined.
- **Bluetooth** lists paired devices, with a search bar.
- **Location** draws a vector OpenStreetMap (MapLibre with OpenFreeMap tiles, no API key), sharp on
  any screen. In dark mode it is repainted in the soft greys of Google Maps' dark theme; water takes
  the wallpaper color. Search for a place by name, pan the map under the pin, set the detection
  range, or jump to where you are.
- **Movement: flying** starts at 100 m/s, or at 50 m/s above 1,500 m, and ends on a fix below
  25 m/s. It works with airplane mode on.

</details>

<details open>
<summary><b>🎬 Mode actions</b></summary>
<br>

A **Mode actions** card under Mode settings runs something when the Mode turns on or off —
however it was switched: by a trigger, a schedule, Quick Settings or by hand. A button group
chooses **Turns on** or **Turns off** for each action.

| Action | What it does |
|---|---|
| **Open an app** | Opens an installed app on the screen. |
| **Send an intent** | Broadcasts an intent action to one named package, with the extras `mode_id`, `mode_name` and `mode_active`. In Tasker, catch it with *Event › System › Intent Received*. |
| **Run a Tasker task** | Runs one of your Tasker tasks by name; the editor lists them. Tasker needs *Preferences › Misc › Allow External Access*. |

Each action also has a **cooldown**: the least time between two runs of it. A Mode can change
several times in a minute for reasons no trigger caused — a schedule boundary, Quick Settings,
another app — and without a cooldown each change opens the app again.

> [!IMPORTANT]
> **Send an intent is a privileged action.** The broadcast is sent by system_server, so every
> receiver sees the system process as its sender and may trust it accordingly. Two rules follow,
> and both are enforced in the editor and again before the broadcast leaves:
>
> - **The action name must be your own.** Anything beginning with `android.`, `com.android.` or
>   `com.google.android.` is refused, so a Mode cannot make an app believe the platform announced
>   a boot, a package change or a SIM state. Use something like `com.example.modes.CHANGED`.
> - **It goes to one named package.** Sending to every app that listens is behind an **Advanced**
>   switch with a warning, because a system-sent broadcast reaching every installed app is a much
>   larger thing than telling one automation app.
>
> A rule written straight into `Settings.Secure`, or left behind by an older build, is checked
> the same way before anything is sent.

<table>
<tr>
<td align="center"><img src="docs/screenshots/action-picker.png" width="250"><br><sub>Choosing an action</sub></td>
<td align="center"><img src="docs/screenshots/action-app.png" width="250"><br><sub>Open an app</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/action-intent.png" width="250"><br><sub>Send an intent</sub></td>
<td align="center"><img src="docs/screenshots/action-tasker.png" width="250"><br><sub>Run a Tasker task</sub></td>
</tr>
</table>

Booting the phone or saving an action never replays an old change: the state a Mode is in when
its actions are loaded is only remembered.

</details>

<details open>
<summary><b>⚙️ System settings</b></summary>
<br>

**Add an action → System settings** holds a device setting at a value for as long as the Mode is
on, however the Mode was switched: a trigger, its schedule, Quick Settings or by hand. It is not
a one-shot command, and it needs no second action to undo it.

| Group | Settings |
|---|---|
| Display | Auto-rotate screen, Dark theme, Brightness level, Adaptive brightness, Extra dim, Screen timeout, Always show time and info, Smooth Display |
| Sound & vibration | Ringer mode (Ring, Vibrate, Silent), Ring, Notification, Media and Alarm volume |
| Connections | Wi-Fi, Bluetooth, NFC, Wi-Fi hotspot, Mobile data |

- **Restore previous value**, on by default: when the Mode ends, the setting goes back to what it
  was before the first Mode took it.
- **Your changes win.** If you change a setting yourself while a Mode holds it, the module does
  not change it back, and does not restore over it when the Mode ends.
- **Several Modes on one setting:** the Mode turned on most recently wins. When it ends, the
  next one still on applies again; when the last one ends, the previous value comes back.
- **Saving** a setting on a Mode that is already on applies it at once; removing it, or deleting
  the Mode, gives the setting back.
- **Booting or updating** never re-applies a setting: a Mode already on keeps the setting it held,
  and one that ended while the phone was off gives it back when the module starts, unless the
  setting changed meanwhile.
- **Mobile data** applies to the SIM chosen for mobile data. If that choice changes while the Mode
  is on, the previous value is not restored onto the other SIM.
- **Sound follows Do Not Disturb.** A Mode whose Do Not Disturb silences calls keeps the ringer,
  ring and notification volume silent while it is on, as Android does; the module cannot override
  that, and restores them afterwards. Raising the ring volume above zero puts the ringer on Ring,
  as the volume slider does.
- **Brightness** uses the slider's own scale. With adaptive brightness on, the phone keeps adjusting
  from that level.
- **Wi-Fi hotspot** goes through your carrier's approval, as Quick Settings does; if it is refused
  the hotspot stays off.
- A setting this phone cannot change is shown greyed out with the reason, and listed on the
  compatibility page. Settings change only while the phone's owner is the user in the foreground.

</details>


<details open>
<summary><b>📅 Calendar event filter</b></summary>
<br>

Android's own **Calendar events** schedule turns a Mode on for every event in a calendar. The
module adds an **Event** row to that page, with three choices:

| Choice | Turns the Mode on for |
|---|---|
| **Any** | every event, as Android does by default |
| **One event** | one event picked from the next 30 days, and every repeat of it |
| **Keyword** | every event whose title contains a word or phrase, ignoring case — including events added later. The editor previews upcoming matches. |

The calendar and invite-reply controls stay Android's own. The filter travels inside the stock
schedule, and system_server applies it where Android checks each event.

<table>
<tr>
<td align="center"><img src="docs/screenshots/calendar-event-filter.png" width="250"><br><sub>Filtering by title keyword</sub></td>
</tr>
</table>

</details>

<details open>
<summary><b>🗑 Remove schedule</b></summary>
<br>

Android lets you give a Mode a schedule, but never take it away again. Both stock schedule
pages, **Day and time** and **Calendar events**, get a trash button at the top right. After a
confirmation the schedule page closes, the Mode goes back to having no schedule, and its page
offers "Set a schedule" again. The Mode's settings, triggers and actions stay.

<table>
<tr>
<td align="center"><img src="docs/screenshots/schedule-remove.png" width="250"><br><sub>The trash button on a schedule page</sub></td>
<td align="center"><img src="docs/screenshots/schedule-remove-confirm.png" width="250"><br><sub>Confirming</sub></td>
</tr>
</table>

</details>

---

## Status

**v0.0.3 — alpha.** Hooks reach into Settings and system_server internals that move between
Android releases. When one moves, the feature that needs it switches itself off, and
**Compatibility & diagnostics** — at the bottom of the Modes list, under *Create your own
mode* — says which one went.

That page reports what this module found when it met the build it is running on: whether it is
active and at what version, the Android build, Settings version, libxposed API and framework
under it, each feature with Compatible or Unavailable and the reason, how many of its hooks
resolved, how many were found by signature after a rename or by a fallback signature, how many
failed — and then every class, method and field it depends on, one row each, with optional ones
marked as such. **Copy compatibility report** puts the
whole thing on the clipboard for a bug report.

### Compatibility

Each row is one build somebody has actually run this on. If you try another, please open a pull
request adding a row — that table is worth more than any claim about "Android 17".

| Device | Android build | Vector | Triggers tested | Actions tested | Tester | Date |
|---|---|---|---|---|---|---|
| Pixel 8 Pro | 17 QPR1 · `CP3A.261005.005` | 2.2 | Wi-Fi, Bluetooth, location, airplane | All 18 system settings | @MrxSiN | 2026-10 |
| Pixel 8 Pro | 17 QPR1 · `CP3A.260905.009` | 2.2 | Wi-Fi, Bluetooth, location, movement, airplane | Open app, send intent, Tasker | @MrxSiN | 2026-09 |

On a device, `scripts/device.sh health` prints the same self-test the page shows:

```bash
scripts/device.sh health
```

Every build in the table is also recorded, as class and member names only, under
`app/src/test/resources/hosts/`, and CI checks every feature's contract against each one. To
record a new build, run `scripts/capture-host-fixture.sh` on it. [ARCHITECTURE.md](ARCHITECTURE.md)
explains the contracts, the fixtures and the security boundary of the editor.

### Known limits

- A scheduled Mode still follows its schedule, which overrides a trigger at a schedule boundary.
  A Mode with both now says so, in a note under its triggers. Triggers work best on Modes without
  a schedule.
- After an update is hot reloaded, triggers are judged afresh, as at boot: a met rule switches its
  Mode on, an unmet one leaves it alone.
- System settings belong to the phone's owner (the system user): with another user in the
  foreground they are left alone.
- **Left still** and **flying** both start out unknown, so a rule reading them is not met until
  the signal has decided: ten quiet minutes for still, one position fix for flying.

## Requirements

| | |
|---|---|
| **Android** | 17 QPR1 (API 37), September 2026 |
| **Device** | Pixel (tested on Pixel 8 Pro) |
| **Framework** | [Vector](https://github.com/JingMatrix/Vector) v2.2+ (libxposed API 102 for hot reload; 101 works with a reboot per update) |
| **Root** | Required by the framework; the module itself asks for none |

Built against the modern [libxposed API](https://github.com/libxposed/api)
(`io.github.libxposed:api`), not the legacy `de.robv.android.xposed` bridge.

## Install

```
1. Install the APK from Releases
2. Enable Pixel Modes Evolved in Vector
3. Reboot once
4. Settings → Modes → a Mode → Mode settings
```

The module declares a **static scope** — System Framework and Settings — so there is nothing to
pick. It has no launcher icon; the trigger editor opens from the Mode page.

Later updates need no reboot: system_server hot reloads the new build, and Settings restarts
itself the next time it is out of sight.

---

## How it works

```
Settings (Mode page rows) ──Settings.Secure "mode_evolved_rules"──▶ system_server
                                                                    rules ─▶ TriggerEngine ─▶ NotificationManager
                                                                    SignalHub (Wi-Fi, Bluetooth, location, movement, airplane) ─┘
```

- **Settings** (scope `com.android.settings`). After `ZenModeFragment` refreshes, the module
  rebuilds its rows in the Mode page's trigger category, using Settings' own `androidx.preference`
  classes by reflection. A row starts the editor for a result. Settings reads saved networks, paired
  devices and the last position, because the editor app has no permission to, and passes them to
  the editor as JSON. Settings runs as the system uid, so it writes rules straight into
  `Settings.Secure`.
- **Editor** (this app). `EditorActivity` answers only Settings and returns one JSON result: save
  or remove, with the Mode id.
- **system_server** (scope `system`). Starts the automation when `NotificationManagerService`
  reaches `PHASE_BOOT_COMPLETED`, on its own thread. It observes the rules and switches Modes through
  the public `NotificationManager.setAutomaticZenRuleState`, which, called as the system uid, can
  drive Modes the system owns. Mode changes are noticed through `zen_mode_config_etag`, which
  Android rewrites after every change, and that is what runs Mode actions.

<details>
<summary><b>Deciding when a Mode switches</b></summary>
<br>

Decisions are edge-triggered: a Mode is switched only when its rule goes from met to not met or
back.

- At boot, a rule that is not met leaves its Mode alone, so a Mode started by hand or by schedule
  keeps running.
- Saving or editing a trigger applies the rule at once, met or not, and ends a manual override.
  Saving a trigger is an explicit choice.
- Removing the last trigger of a Mode that the triggers held on turns that Mode off.
- A rule with a wait holds an edge the signals caused for that long and drops it if the answer
  comes back. Load and edit never wait.

Location requests are attributed as `android[PixelModesEvolved]`, visible in `adb shell dumpsys location`.

</details>

<details>
<summary><b>Not acting on what the device no longer knows</b></summary>
<br>

Three signals used to answer before they knew anything, which switched Modes for a few minutes
after every boot:

- **Left still** means "has not moved for 10 minutes". At the moment the signal starts nobody
  knows that yet, so movement begins *unknown* and still is published only once the significant
  motion sensor has stayed quiet for the full ten minutes. Motion restarts the count.
- **Position fixes** arrive with the provider's last known one first, which can be hours old.
  Every fix is weighed against its age and dropped when it is too old: ten minutes for a location
  rule, two for flight detection, where a speed from before takeoff would start or end a flight at
  the wrong time.
- **An area** is judged from a fix that carries its accuracy, not from a bare point. Leaving needs
  clearing a wider boundary than arriving did — a quarter of the radius, or the fix's own accuracy
  when that is larger — so a 100 m-accurate fix against a 50 m area settles on one answer instead
  of flapping across the edge every few minutes.

</details>

<details>
<summary><b>Surviving a quarterly release</b></summary>
<br>

Every hook is installed through one guard that records the outcome, and every class, method and
field it looks for is recorded with it. Each process writes its own half to `Settings.Secure`;
the Settings app adds them together, because it is the only one that can see the Android build,
the framework and both halves at once, and hands the whole report to the page.

Classes inside Settings are reached by name first, which costs one `loadClass` on a build that
matches. When a name has gone, [DexKit](https://github.com/LuckyPray/DexKit) searches that app's
own dex for a class in the same package declaring the same methods, and the hook carries on with
whatever it was renamed to. A search that finds more than one candidate finds nothing: hooking
the wrong class inside Settings is worse than losing one feature.

The search is opened only after a name has actually failed, and closed as soon as it has answered,
so a matching build never pays for it.

</details>

<details>
<summary><b>Updating without a reboot</b></summary>
<br>

- **system_server** is hot reloaded by Vector (`autoHotReload=true` in `module.prop`). Vector only
  reloads when the installed `versionCode` differs from the loaded one, so every build gets its own:
  seconds since 2026-01-01, taken from the commit's date in CI and from the clock locally, so
  rebuilding a tag produces the same APK. The old build stops its automation — including any edge
  still waiting out a rule's stabilization — and hands the system_server class loader and system
  context to the new one (`SystemServerPart`), which hooks again and restarts the automation at once.
- **Settings** declines hot reload, since its pages hold views and callbacks of the build that drew
  them. When the module is replaced, Settings stops itself as soon as none of its screens is visible
  (`ModuleUpdateRestarter`), so it opens on the new build next time. It never stops while on screen.
- For a moment after an update, old hook code in Settings reads the new APK's resources. Resource
  IDs are pinned in `app/resource-ids.txt` (aapt2 `--stable-ids`), so they never shift between
  builds. **Commit that file**: CI runs `git diff --exit-code -- app/resource-ids.txt` after the
  release link and fails the build when a new resource left it uncommitted.

</details>

---

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Linking the release refreshes `app/resource-ids.txt`. If it changed, commit it — CI fails the
build otherwise. Set `VERSION_CODE` to pin the version code; without it a local build uses the
clock.

Install the release build for daily use. The debug build is about 14 times larger and slow to
start, because Compose runs unoptimised in it. Release builds are shrunk with R8 and signed with
the local debug key; the module entry class is kept by name, because the framework resolves it from
`META-INF/xposed/java_init.list`.

`scripts/device.sh` installs and diagnoses over adb (root and Vector required):

```bash
scripts/device.sh install   # build, install, enable in Vector, set scope
scripts/device.sh check     # Vector state, scope, stored rules, recent log lines
scripts/device.sh logs      # follow the module's log
scripts/device.sh rules     # print the stored rules
scripts/device.sh modes     # each Mode's live state
```

Each switch logs `Mode <id> -> on|off`, and each input logs `Signal X: start|stop`.

## Design

```
ModeEvolvedModule   Xposed entry: only routes system_server and Settings to their parts
compat/             every private member each feature needs, per host; fixtures and checks read it
store/              shared storage over Settings.Secure
trigger/            pure domain: trigger kinds, device state, rules — no Android code
dex/                resolving contracts: by name, then a structural search, never an ambiguous one
security/           who the exported editor and compatibility page answer
rule/               rule codec and one TriggerKind per trigger
action/             Mode actions, their codec, and ActionAutomation
engine/             TriggerEngine decides, SignalHub runs inputs, ModeAutomation wires them
signal/             one SignalSource per Android input
system/             system_server: boot and hot reload, Mode switching, action running
calendar/           the calendar event filter and its codec
presentation/       label, icon and description per kind, shared by rows and editor
page/               Settings: Mode page rows, reflection boundaries, editor launch and results
diagnostics/        the compatibility page and the report the Settings side gathers for it
editor/             the Compose editor app, one KindEditor per kind
```

To add a trigger, add one `Trigger`, one `TriggerKind`, one `TriggerPresentation`, one
`KindEditor` and, if it reads a new input, one `SignalSource`. To add an action, add one
`ModeAction`, one `ActionKind`, one `ActionPresentation`, one `KindEditor` and one `Performer` in
`IntentActionRunner`. Then register each in its list; nothing existing changes.

To hook something new in Settings or system_server, declare the member in `compat/SettingsApis.kt`
or `compat/SystemServerApis.kt`, add it to its feature in `compat/FeatureContracts.kt`, resolve it
through `HostResolver`, and run `scripts/check-project.sh`. The layering, the concurrency rule
and the threat model are in [ARCHITECTURE.md](ARCHITECTURE.md).
