# S1 MTR Addon — NeoForge 1.21.1 port

This is a source-level port of the uploaded Fabric 1.20.1 addon to:

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- Minecraft Transit Railway `NEOFORGE-4.1.0-beta.2+1.21.1`

## Features retained

- Editable rail speed limit (1–1000 km/h)
- Delayed platform-door opening (0–60 seconds)
- Delayed departure after door closing (0–60 seconds)
- Preservation of custom values when changing rail geometry or rail style

Delay values remain embedded in the rail style list as:

```text
s1mtr:doorOpenDelay=<seconds>
s1mtr:doorCloseDelay=<seconds>
```

## Build

Use Java 21, then run:

```bash
./gradlew clean build
```

The output should be under `build/libs/`.

See `PORTING_REPORT.md` for compatibility risks and test steps. The original Fabric source is retained under `reference/original-fabric-1.20.1/`.
