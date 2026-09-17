# Pixel Modes Evolved v0.0.1

First alpha. Tested on one device: a Pixel 8 Pro on **Android 17 QPR1 (September 2026)**,
build `CP3A.260905.009`, with Vector v2.2. Expect rough edges, and keep a way to recover your
phone.

### What's in it

- **Triggers** on each Mode's page: Wi-Fi network, Bluetooth device, location, movement (on the
  move, left still, flying) and airplane mode, joined by AND / OR.
- **Mode actions** when a Mode turns on or off: open an app, send an intent, run a Tasker task.
- **Calendar event filter** for the stock Calendar events schedule: any event, one event, or a
  title keyword.
- **Remove schedule**: a trash button on the stock Day and time and Calendar events pages.
- **Location map** with a Google Maps-style dark theme.
- **No reboot to update**: system_server hot reloads a new build, and Settings restarts itself
  once it is out of sight.

### Install

1. Install `ModeEvolved-v0.0.1.apk`
2. Enable ModeEvolved in Vector (static scope: System Framework and Settings)
3. Reboot once
4. Settings → Modes → a Mode → Mode settings

### Notes

- Needs Vector v2.2+ (libxposed API 102). Frameworks with API 101 work, but each update needs a
  reboot.
- If a build signed with a different key is installed, uninstall it first. Triggers and actions
  are stored in `Settings.Secure` and survive.
