# Spec: example-audio links behind malenia (desktop part)

Status (2026-11-13): **Increment 1 implemented on branch `example-audio-links` (off `old_ui`),
tests green, not committed or released yet.** Web side, release 1.0.2 and deploy are tracked in the
web repo (`docs/app_release_page/spec.md`) and in `docs/HANDOFF-2026-11-13-example-audio-links.md`.

## Problem
Clinics built with `serverBaseUrl=https://malenia.feld.cvut.cz/koubic` get no example-audio button:
the backend sends `audioExamplePath` as an absolute URL built from `ASSET_BASE_URL`, the internal
address `http://192.168.122.183:10001`, which clinics cannot reach.

## Decisions (desktop)
1. The backend sends the relative `audio/illustrations/<key>.wav` to apps ≥ **1.0.2**; older or
   unknown clients keep getting the absolute URL. The app resolves relative paths against
   `SERVER_BASE_URL` (the build's server base, no `/api`).
2. Resolution lives inside `KtorExampleAudioCache`: one `resolveExampleUrl(raw, base)` used by
   `sync`, `localFileFor` and pruning, so the cache key is always `sha256(resolved URL)`.
   - Absolute http(s) URL with a host → returned unchanged (existing cache keys stay valid).
   - Otherwise a path relative to the base; a leading `/` is still relative to the base (keeps
     `/koubic`). The normalized result must be http(s), on the base host/port, and under the base
     path; `..` escapes (also `%2e%2e`) are rejected.
   - Other schemes (`file:` …), blank or unparsable values → rejected (no example button).

## Acceptance criteria (desktop)
- **A1.** Unit tests (`KtorExampleAudioCacheTest`): with base `https://malenia.feld.cvut.cz/koubic`,
  `audio/illustrations/phonation_a.wav` and `/audio/illustrations/phonation_a.wav` both resolve to
  `https://malenia.feld.cvut.cz/koubic/audio/illustrations/phonation_a.wav`; absolute URLs are
  unchanged; `../../x.wav` and `file:` are rejected; `sync`, `localFileFor` and prune agree on the key.
- **A2.** `./gradlew :app:test :updater:test :shared:test` is green.
- **A7.** A 1.0.2 build pointed at the test container plays the example (after web deploy).
- **A9.** `docs/Task_Configuration_JSON_Spec.md` §4.1 amended; `docs/Project_Specification.md`
  decision 51 amended only after the wording is approved.

## Changes
- `app/.../infrastructure/network/KtorExampleAudioCache.kt`: `resolveExampleUrl`, constructor
  parameter `serverBaseUrl` (default `SERVER_BASE_URL`; `AppContainer` uses the default).
- `app/.../domain/config/ExampleAudioCache.kt`: KDoc allows relative paths.
- `gradle.properties`: `appVersion=1.0.2`.

## Remaining (not desktop worker)
- Approve `Project_Specification.md` decision 51 wording, release 1.0.2 (`docs/dev/releasing.md`),
  web change + deploy, outside-network check (A8).
