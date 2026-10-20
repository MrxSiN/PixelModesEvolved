# Migration

Nothing to migrate. The persistent formats are untouched:

- `Settings.Secure mode_evolved_rules` and `mode_evolved_actions`: same JSON, same codecs,
  including the legacy `match` field and dropping of unknown trigger kinds.
- Diagnostics keys and `resource-ids.txt`: unchanged (the release build left the file identical).

The compiled rule form lives only in memory and is rebuilt from storage at every load, so a
downgrade or upgrade reads exactly what it always read.

Hot reload: a new build's `takeOver` starts a fresh engine, which loads and compiles the stored
rules as at boot; the old build's `handOff` stops its engine first. No compiled state crosses the
hand-off, as before.

## Feature 01: system settings

- Actions gain a kind, `{"type":"systemSetting","setting":"<id>","value":<int>,"restore":<bool>}`,
  in the same `mode_evolved_actions` entry. Older records parse unchanged; `restore` absent means
  true. An unknown setting id or an out-of-range value drops that one action (tested). An older
  build reading a newer record drops the unknown kind and keeps the rest, as before.
- New entry `Settings.Secure mode_evolved_setting_claims`: who holds which setting, from what
  baseline. Written only after a system setting was claimed; cleared (null) when nothing is
  claimed. An unreadable value is ignored, which means no restoration, never a wrong one.
- Setting ids are stable strings, separate from enum order, so they survive reordering.
- `resource-ids.txt` gained the new strings and drawables; no existing ID moved.

## v0.0.4

- No stored format changed. Rules, actions and claims read exactly as before; a flying trigger
  keeps its record and is judged by the new detector.
- A Mode given one of the extra icons stores it the way Android stores every Mode icon, as a
  framework resource name (`android:drawable/ic_qs_airplane`). A downgrade, or removing the module,
  keeps the icon: only the picker loses the extra choices.
- `resource-ids.txt` gained the new strings; no existing ID moved. Two entries for strings that
  were added and dropped during development stay pinned, as every ID does.
