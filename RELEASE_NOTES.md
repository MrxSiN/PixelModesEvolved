# Pixel Modes Evolved v0.0.4

Fourth alpha: the flying trigger now actually notices a flight, and custom Modes get many more
icons. Tested on a Pixel 8 Pro on **Android 17 QPR1 (October 2026)**, build `CP3A.261005.005`,
with Vector v2.2. Expect rough edges, and keep a way to recover your phone.

### Flying, from cabin pressure

- **Movement: flying** used to wait for a satellite fix with a speed, which a phone in a pocket
  inside a cabin rarely gets. It now reads the **barometer**: an airliner's cabin climbs about
  850 m within minutes of takeoff, and the pressure falls by 100 hPa within 12 minutes. No road
  climbs that fast, so driving, trains, lifts and weather do not count.
- Speed still works where there is a fix, but needs two fast fixes in a row, so one bad
  satellite reading is no longer a flight.
- A flight ends once the cabin has descended and the pressure has held steady for 10 minutes, on a
  slow fix once the cabin no longer shows cruise, or after 20 minutes with no sign of flight. A
  landing seen only through Wi-Fi or cell positions no longer leaves the Mode on for good.
- The barometer is read at its slowest rate into the sensor hub's own buffer and never wakes the
  phone. Phones without one keep the speed-only detection.

### More Mode icons

- The icon picker for a Mode you create offers **43 more icons** after Android's 40: camping,
  croissant, airplane, flashlight, battery, Bluetooth, night light, alarm, laptop, headset,
  camera, microphone, phone, thermostat, padlock, satellite, location pin, printer, music and more.
- They are Android's own drawables, so the status bar, Quick Settings and lock screen show them,
  and a Mode keeps its icon after an update or if the module is removed.

### Fixes

- AppOps no longer logs `attributionTag PixelModesEvolved not declared in manifest of android` on
  every location delivery: requests are attributed to a tag the `android` package declares.
- A Mode page no longer parses the stored rules again when nothing changed, and AND/OR rows no
  longer look their views up by name on every bind.

### Install

1. Install `PixelModesEvolved-v0.0.4.apk`
2. Enable Pixel Modes Evolved in Vector (static scope: System Framework and Settings)
3. Reboot once, or let system_server hot reload if you are updating
4. Settings → Modes → a Mode → Mode settings

### Notes

- Upgrading keeps your triggers, actions and Mode icons.
- If a build signed with a different key is installed, uninstall it first.

---

# Pixel Modes Evolved v0.0.3

Third alpha: Modes can now change device settings, and the module does less work inside
system_server. Tested on a Pixel 8 Pro on **Android 17 QPR1 (October 2026)**, build
`CP3A.261005.005`, with Vector v2.2. Expect rough edges, and keep a way to recover your phone.

### System settings while a Mode is on

- **Add an action → System settings**: auto-rotate, dark theme, brightness, adaptive brightness,
  Extra dim, screen timeout, always show time and info, Smooth Display, ringer mode, ring,
  notification, media and alarm volume, Wi-Fi, Bluetooth, NFC, Wi-Fi hotspot and mobile data.
- Each is held for as long as the Mode is on, however it was switched, and **restored** when it
  ends, unless you changed it yourself meanwhile. No second action is needed to undo it.
- Several Modes on one setting: the one turned on last wins, and the next one takes over when it
  ends. Booting or updating never re-applies a setting.
- A Mode whose Do Not Disturb silences calls keeps the ringer silent while it is on, as Android
  does. Mobile data follows the SIM chosen for mobile data. The hotspot goes through your
  carrier's approval.
- Each setting is checked on your build at boot and listed on the compatibility page; one your
  phone cannot change is greyed out in the editor with the reason.

### Less work inside system_server

- Rules are compiled when you save them. A signal change re-checks only the rules that read it,
  without allocating: 7 to 16 times less CPU per event on a Pixel 8 Pro.
- Wi-Fi signal-strength updates and repeated Bluetooth broadcasts stop at the source instead of
  re-running every rule.
- No new sensor, location request, alarm, wakelock or polling. Idle cost measured the same.

### Safer screens and contracts

- The editor and the compatibility page answer only Settings itself, judged from what the system
  recorded about the caller, never from what the intent claims.
- Every private member a feature uses is declared once, with the feature it belongs to; a feature
  installs only when everything it requires resolves, and CI checks each against the recorded
  build.

### Install

1. Install `PixelModesEvolved-v0.0.3.apk`
2. Enable Pixel Modes Evolved in Vector (static scope: System Framework and Settings)
3. Reboot once, or let system_server hot reload if you are updating
4. Settings → Modes → a Mode → Mode settings

### Notes

- Upgrading keeps your triggers and actions. Older builds ignore system-setting actions and keep
  the rest.
- If a build signed with a different key is installed, uninstall it first.

---

# Pixel Modes Evolved v0.0.2

Second alpha, and mostly about not acting on what the phone does not yet know. Tested on a
Pixel 8 Pro on **Android 17 QPR1 (September 2026)**, build `CP3A.260905.009`, with Vector v2.2.
Expect rough edges, and keep a way to recover your phone.

### Signals no longer answer before they know

- **Left still** begins *unknown* rather than still. It means "has not moved for 10 minutes", and
  nobody knows that at the moment the rule starts, so the answer is withheld until the motion
  sensor has actually stayed quiet that long. Until now a "left still" rule was met the instant
  the phone booted, or the instant you saved it.
- **Position fixes** are weighed against their age. The provider answers with its last known fix
  first, which can be hours old; one that stale would switch a Mode on for the minutes until the
  current fix lands, and edge-trigger its actions on the way. Ten minutes for a location rule,
  two for flight detection, where a speed from before takeoff would start or end a flight at the
  wrong time.
- **Areas** are judged from a fix that carries its accuracy, not from a bare point. Leaving now
  takes more than arriving did — a quarter of the radius, or the fix's own accuracy when that is
  larger — so a 100 m-accurate fix against a 50 m area settles on one answer instead of flapping
  across the edge every few minutes.

### Noisy signals no longer run your actions twice

- **Wait before switching**, under the triggers on each Mode's page: off, 30 seconds, 1, 2 or 5
  minutes. Wi-Fi roaming between access points, a headset reconnecting and a fix wandering along
  a boundary all produce real edges, and without a wait each of them switches the Mode and runs
  its actions. Saving a trigger still applies at once — that is you, not a noisy signal.
- **Cooldown** on each action: the least time between two runs of it. A Mode also changes for
  reasons no trigger caused — a schedule boundary, Quick Settings, another app — and a cooldown
  keeps those from opening the same app three times in a row.

### Send an intent is treated as the privileged action it is

The broadcast leaves system_server, so every receiver sees the system process as its sender.

- The action name must be your own. Anything beginning with `android.`, `com.android.` or
  `com.google.android.` is refused, so a Mode cannot make an app believe the platform announced
  a boot, a package change or a SIM state.
- It goes to one named package. Sending to every app that listens is now behind an **Advanced**
  switch with a warning.
- Both rules are enforced in the editor and again before the broadcast leaves, so a rule left by
  an older build, or written straight into `Settings.Secure`, is checked the same way.

### Compatibility & diagnostics

A new page at the bottom of the Modes list, under *Create your own mode*. It reports what the
module found when it met the build it is running on: whether it is active and at what version,
the Android build, Settings version, libxposed API and framework under it, every feature with
**Compatible** or **Unavailable** and the reason, how many hooks resolved, how many were found
by signature after a rename, how many failed — and then every class, method and field it depends
on, one row each. **Copy compatibility report** puts the whole thing on the clipboard.

Previously a moved private API cost one feature and said so only in logcat.

### Following Settings when it moves

Classes inside Settings are looked up by name first, which costs one `loadClass` on a build that
matches. When a name has gone, [DexKit](https://github.com/LuckyPray/DexKit) searches that app's
dex for a class in the same package declaring the same methods, and the hook carries on with
whatever it was renamed to. A search finding more than one candidate finds nothing: hooking the
wrong class inside Settings is worse than losing one feature.

### Also

- A Mode with both an Android schedule and triggers now says so, in a note under its triggers,
  rather than leaving a schedule boundary looking like a bug.
- Releases are reproducible: the version code comes from the commit being built, so rebuilding a
  tag produces the same APK.
- CI fails the release when building changes `app/resource-ids.txt` without it being committed,
  which is what hot reload depends on.

### Install

1. Install `PixelModesEvolved-v0.0.2.apk`
2. Enable Pixel Modes Evolved in Vector (static scope: System Framework and Settings)
3. Reboot once, or let system_server hot reload if you are updating
4. Settings → Modes → a Mode → Mode settings

### Notes

- Needs Vector v2.2+ (libxposed API 102). Frameworks with API 101 work, but each update needs a
  reboot.
- Upgrading from v0.0.1 keeps your triggers and actions; both are stored in `Settings.Secure`.
- If a build signed with a different key is installed, uninstall it first.
