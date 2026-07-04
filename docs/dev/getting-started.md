# Getting started

SHARE clinical recording app — Kotlin/JVM, Compose Multiplatform for Desktop, Decompose.
The normative spec is [`docs/Project_Specification.md`](../Project_Specification.md); the
remote-config format is [`docs/Task_Configuration_JSON_Spec.md`](../Task_Configuration_JSON_Spec.md).
All design decisions of record live in spec **§13** — check there before changing behavior.

## Build, run, test

```bash
./gradlew :app:run        # launch the desktop app
./gradlew :app:test       # full suite (unit + component + headless UI scenarios)
./gradlew :app:test --tests "org.example.app.domain.*"   # targeted filter
./gradlew :updater:test :shared:test   # updater + shared version-model tests
```

- JDK: whatever Gradle's toolchain resolves (developed on Temurin 25).
- Tests run headless out of the box: `tasks.test` in `app/build.gradle.kts` sets
  `-Dskiko.renderApi=SOFTWARE -Djava.awt.headless=true`. No Xvfb needed (verified with
  `DISPLAY` unset); see [`testing.md`](testing.md) for details and the fakes inventory.
- Test logging goes to console only (`app/src/test/resources/logback-test.xml`); production
  logs go to `data/logs/app.log` (`app/src/main/resources/logback.xml`).

## Modules

```
:app       all product code (org.example.app)
:shared    version model shared with the updater
:updater   minimal auto-updater (§9 — see updater.md)
```

## Runtime data

Everything the app persists lives under `<working dir>/data/` (so `app/data/` when run via
Gradle): `config/` (cached remote config + settings), `sessions/<date_patient_session>/`
(one dir per examination — see [`data-and-schemas.md`](data-and-schemas.md)), `logs/`.
The layout is spec §8.2; the only source of these paths in code is the `AppDirectories`
port (`domain/AppDirectories.kt`) — nothing else builds paths.

## Where to go next

- [`architecture.md`](architecture.md) — layers, DI, threading rules
- [`data-and-schemas.md`](data-and-schemas.md) — every persisted file, with examples
- [`cookbook.md`](cookbook.md) — how to change/add things, with worked examples
- [`ui.md`](ui.md) — visual language, screen inventory, "add a screen" checklist
- [`testing.md`](testing.md) — test layers, harness, "add a scenario" walkthrough
- [`updater.md`](updater.md) — `:updater` flow, install layout, failed-launch recovery
- [`packaging-and-deployment.md`](packaging-and-deployment.md) — native images, signing, server-contract seams
