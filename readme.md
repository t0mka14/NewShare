# SHARE Clinical Recording App (rewrite)

Desktop app (Kotlin/JVM, Compose Multiplatform, Decompose) for recording speech data from
participants in clinical protocols: patient info → calibration → guided tasks over one
continuous master WAV → optional waveform editor → per-task clip export + ZIP archive →
manual upload to a REST server. Remotely configured per installation.

```bash
./gradlew :app:run    # launch
./gradlew :app:test   # full test suite (headless-safe)
```

## Documentation

- **[docs/Project_Specification.md](docs/Project_Specification.md)** — the normative spec;
  all decisions of record in §13
- [docs/Task_Configuration_JSON_Spec.md](docs/Task_Configuration_JSON_Spec.md) — remote config format
- [docs/dev/getting-started.md](docs/dev/getting-started.md) — build, run, test, layout
- [docs/dev/architecture.md](docs/dev/architecture.md) — layers, DI, threading rules
- [docs/dev/data-and-schemas.md](docs/dev/data-and-schemas.md) — every persisted file
- [docs/dev/cookbook.md](docs/dev/cookbook.md) — how to change things, with examples
- [docs/dev/ui.md](docs/dev/ui.md) — visual language + "add a screen" checklist
- [docs/dev/testing.md](docs/dev/testing.md) — test layers, fakes, scenario harness
- [docs/dev/updater.md](docs/dev/updater.md) — `:updater` flow, install layout, failed-launch recovery
- [docs/dev/packaging-and-deployment.md](docs/dev/packaging-and-deployment.md) — native images, signing/notarization, server-contract seams

The original app being rewritten lives at `/home/tomas/IdeaProjects/shareapp` (reference
for UI fidelity, §13 decision 36).
