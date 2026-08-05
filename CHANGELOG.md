# Changelog

## 1.1.0-neoforge.1

- Ported project from Fabric 1.20.1/Yarn/Java 17 to NeoForge 1.21.1/Mojang mappings/Java 21.
- Targeted MTR 4.1.0-beta.2 NeoForge artifact.
- Rebuilt settings UI with vanilla 1.21.1 widgets.
- Updated current MTR package names.
- Added compatibility diagnostics and safer optional injections.
- Preserved the original uploaded source under `reference/`.

## 1.2.3-neoforge.1
- Read numeric fields directly when Done is pressed.
- Prefer the exact MTR request/packet constructors and support fluent addRail return values.
- Close the stale parent MTR editor after a successful save to prevent old rail data from being resubmitted.
- Add visible save failure messages and detailed network/reflection diagnostics.
- Preserve rail signal colors when supported by the installed MTR core.
