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
