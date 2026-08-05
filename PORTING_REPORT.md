# Porting report

## Evidence-backed target

- MTR publishes a NeoForge build specifically for Minecraft 1.21.1 named `NEOFORGE-4.1.0-beta.2+1.21.1`.
- Its official Modrinth Maven coordinate is `maven.modrinth:XKPAmI6u:9SDO8rYc`.
- The official NeoForge 1.21.1 MDK uses NeoGradle userdev and Java 21.
- Current MTR source uses direct Mojang-mapped Minecraft classes and packages such as `org.mtr.screen`, `org.mtr.client`, `org.mtr.packet`, and `org.mtr.registry`.

## Changes made

1. Replaced Fabric Loom, Fabric Loader and Fabric API with NeoGradle/NeoForge.
2. Replaced `fabric.mod.json` with `META-INF/neoforge.mods.toml`.
3. Replaced the Fabric `ModInitializer` with a NeoForge `@Mod` entry point.
4. Replaced Yarn/Fabric UI classes with Mojang-mapped 1.21.1 classes (`Screen`, `EditBox`, `Button`, `GuiGraphics`).
5. Updated MTR screen target packages from `org.mtr.mod.screen.*` to `org.mtr.screen.*`.
6. Removed the old `SavedRailScreenBaseMixin`; that class is not present in the current MTR 4.1 screen source tree.
7. Added a small reflective packet bridge for the MTR 4.1 client packet API, keeping compile-time coupling limited.
8. Kept the transport-simulation-core mixins optional (`require = 0`) because these private method descriptors are not part of a stable public API.
9. Added startup diagnostics for expected MTR classes, fields, methods and Rail constructor shape.
10. Changed the simulation delta holder from one global static value to `ThreadLocal<Long>`.

## High-risk compatibility points

The following depend on private Transport Simulation Core implementation details:

- `PathData.dwellTime`
- `Vehicle.vehicleExtraData`
- `Vehicle.railProgress`
- `Vehicle.elapsedDwellTime`
- `Vehicle.doorCooldown`
- `Vehicle.simulateStopped(...)`
- calls to `VehicleExtraData.openDoors()` and `Vehicle.startUp(long,long)`

If MTR 4.1.0-beta.2 changed any descriptor, NeoForge can still start because the injections are optional, but the corresponding delay feature will not activate. Check `latest.log` and run the test matrix below.

## Required test matrix

1. Start a dedicated server and client with only NeoForge, MTR, and this addon.
2. Open an MTR rail modifier screen and verify the advanced-settings button appears.
3. Save a normal rail speed, reopen the screen, and verify persistence.
4. Change the rail curve/radius and verify the custom speed remains.
5. Change the rail style and verify speed and delay markers remain.
6. Configure a platform with a 5-second open delay and verify doors stay closed for about 5 seconds after stopping.
7. Configure a 3-second departure delay and verify the train waits after doors finish closing.
8. Restart the world/server and repeat persistence checks.
9. Verify another client without operator permissions cannot alter rails unexpectedly.

## Build verification status

The source tree and resources were generated and statically inspected in the sandbox. A complete Gradle compilation was not possible there because external Gradle/Maven dependency resolution was unavailable. Build it on an internet-connected Java 21 environment and use any compiler/runtime errors to pin the remaining private MTR 4.1 descriptors.
