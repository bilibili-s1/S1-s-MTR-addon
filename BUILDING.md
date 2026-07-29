# Building and debugging

## Prerequisites

- JDK 21
- Internet access to NeoForged Maven, Maven Central, Gradle distributions and Modrinth Maven

## Commands

```bash
./gradlew --refresh-dependencies
./gradlew clean build
```

Run a development client:

```bash
./gradlew runClient
```

Run a development server:

```bash
./gradlew runServer
```

## Dependency pinning

The project pins the official MTR NeoForge 1.21.1 Modrinth version ID `9SDO8rYc`. Do not replace it with the Fabric artifact.

Modrinth Maven does not provide transitive dependency metadata. If MTR's published jar stops bundling a required library, add that library explicitly or place the exact official MTR jar in `libs/` and switch the Gradle dependency to `implementation files("libs/<jar-name>.jar")`.

## Diagnosing failed mixins

Search `latest.log` for:

```text
s1mtraddon
Mixin
RailModifierScreen
PathDataMixin
VehicleSimulateMixin
```

A UI-only failure normally points to changed `org.mtr.screen` internals. A door-delay failure normally points to changed Transport Simulation Core method descriptors or private fields.
