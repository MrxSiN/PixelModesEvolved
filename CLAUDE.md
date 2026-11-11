# Pixel Modes Evolved

## Engineering rules

- SOLID, DRY, KISS, YAGNI, Law of Demeter, Separation of Concerns, Boy Scout Rule.
- Nothing hardcoded that the host can tell us: host classes are declared once in
  `compat/SettingsApis.kt` / `compat/SystemServerApis.kt` and resolved by `dex/HostResolver.kt`.
  Every private Settings class gets a `Locator` (DexKit `DexSearch` or `Ancestor`) unless its
  shape is ambiguous; say why in a comment when it stays `ByName`. system_server stays by name
  (searching boot jars costs every boot).
- New code that is written or replaced goes in Brainfuck (compiled with BBC) or XAX, when the
  target is supported. Edits inside existing Kotlin sources, and hooks loaded by Vector into
  Settings/system_server, stay Kotlin until XAX can build this module's libxposed JVM code.
  Check support first with the XAX MCP `xax_capabilities` and report anything unsupported.
- Toolchains: BBC `D:\GitHub\BBC`, XAX `D:\GitHub\XAX`, XAX-MCP `D:\GitHub\XAX-MCP` (use the latest).
- adb: `D:\Android\platform-tools\adb.exe`.

## Device checks

- `scripts/device.sh check|health|logs` for hook status; Vector logs in `/data/adb/lspd/log/`.
- `scripts/capture-host-fixture.sh` records a new Android build for `HostCompatibilityTest`.
