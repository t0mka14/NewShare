# Fix round 4 (2026-10-30): triage group C

## Goal

Close the 25 small, clear findings in group C of `triage-2026-10-29.md`, so that what's left open
is only the product questions in group D.

## Deliverable

- **Web:** fixes and tests in `task_protocoller_web_app` (branch `newshare`).
- **Desktop:** fixes and tests in NewShare `app/`.
- **Records:** `findings.md` and each finding's report entry updated in F-051 style, with the cause
  and the regression test names.
- **Re-test reports:** run 15 for the web and run 16 for the desktop.

## Decisions (2026-10-30)

- **F-071:** a minimal restyle only. The pill becomes a UI-kit button and the labels lose their
  all-caps; the layout stays as it is.
- **F-100:** re-adding an existing editor returns 200 with the existing grant. The email match
  stays case-insensitive.
- **F-109:** the desktop records the missing camera in `examination.json`, and the web camera tile
  shows it as one translated sentence.

## Acceptance criteria

### Web (`task_protocoller_web_app`)

1. **F-090.** The "Connect a computer" panel shows an absolute address: `VITE_API_BASE`
   resolved against the page URL, for example `https://host/app/api`. It never shows `./api`.
   Test: a component test with base `./api` and a stubbed location.
2. **F-070.** The four questionnaire-modal strings come from i18n in en, cs and de:
   - "Allow write-in";
   - "Remove option";
   - "Exclusive Option (Clears other selections)";
   - "-- None --".

   The key-parity test passes. Test: rendered in cs, none of the English strings appear.
3. **F-071.**
   - "+ Add question" is a standard UI-kit button, not a full-width purple pill.
   - No label in the modal uses `text-transform: uppercase`.

   Test: a component test, or the Playwright check of class and style.
4. **F-084.** The Library "Not available" text addresses people who may write protocols, not only
   administrators. It is updated in en, cs and de. Test: a string assertion.
5. **F-098.** On an empty Clinics page, a user who can't create clinics sees "ask an
   administrator", with no "Create one" text. A user who can create clinics sees the current text.
   Test: a component test for both personas.
6. **F-101.** A hidden or unknown clinic shows "Clinic not found, or you don't have access to it",
   matching the study wording, not the generic 404. Test: a Playwright or component case.
7. **F-112.** The participant panel shows a field's label from the protocol or the catalogue, in
   the viewer's language. The `year_of_birth_opt` field shows "Year of birth". When no label exists,
   it falls back to the key without its `_opt` suffix. Test: a `ParticipantPanel` test.
8. **F-115.** The quarantine banner reads "<lead>: <sentence>" or puts the sentence on its own
   line, so the two never run together. Test: a text assertion.
9. **F-082.** A clinic name at the 64-character limit shows a counter or a limit message. Nothing
   is cut off without notice. This applies to the New clinic modal and the clinic settings.
   Test: a component test.
10. **F-099.** At 1440 px, the Recordings search placeholder is fully visible for a one-clinic
    user. This is done with a shorter placeholder or a minimum width. Test: Playwright at 1440,
    where the input's `scrollWidth` is no larger than its `clientWidth` with the placeholder.
11. **F-078.** A signed-in user with a valid session who opens `#/login` lands on `/recordings`,
    or on `/setup-account` while a password change is pending. The session is kept. Sign out still
    lands on the login page with the session cleared. Test: a Playwright case.
12. **F-100.** `POST` to add an editor who already has a grant returns 200 with the existing grant,
    including its real `added_at`. A new editor still returns 201. Test: a controller test.
13. **F-109 (web part).** A `camera` / `unavailable_at_start` hardware event shows in the camera
    tile as one translated sentence (en, cs, de), with the raw detail behind "Details". Test:
    `Recording.test.jsx`.

### Desktop (NewShare `app/`)

14. **F-046.** When any session in an upload run failed, the progress bar uses the error colour,
    or doesn't count failed sessions as done. It is never a full green bar over a failure.
    Test: a UI or state test.
15. **F-064.** The ready count uses plural forms:
    - EN: 1 session, 2 sessions;
    - CS: 1 sezení (the word is the same in all forms, so check that a 1/2–4/5+ rule exists for
      the key pattern).

    Test: a `BuiltinStrings` or formatter test.
16. **F-065.** A fresh questionnaire shows no validation error. Errors appear after the question is
    touched, or after Next is pressed with invalid answers. Test: a UI test.
    *Change during the round (2026-10-30):* no question is marked as required, so a disabled Next
    on a fresh form gave no reason. A questionnaire's Next is now always pressable. With invalid
    answers it shows every error and stays on the questionnaire.
17. **F-088.** The Settings language options show "Čeština" and "English", the same names as the
    main-menu selector. Test: a UI test reading the option texts.
18. **F-089.** The "No site token" message on the main menu is centred. Test: a UI test that the
    text is centred (`TextAlign.Center`, or the node is centred within ±2 px).
19. **F-105.** The editor end time and length are rounded, not truncated: 62.887 s shows as
    `1:02.9` and 8.987 s shows as `9.0 s`. Test: a formatter unit test.
20. **F-109 (desktop part).** When the protocol wants video and no camera is found at session
    start, `examination.json` `hardware.events` gets
    `{device: "camera", event: "unavailable_at_start"}`. The spec's `examination.json` section
    lists the event. Test: a `SessionComponent` or bootstrap test.
21. **F-133.** The task screen reserves the line for the camera message, so its appearance doesn't
    move the instruction panel or the level meter. Test: a UI test comparing bounds before and
    after the message appears.
22. **F-025.** Editor segment labels name the repetition, for example "Recording 2 of 4 –
    PHONATION, repetition 2". A task with one repetition shows no repetition text. The wording is
    in EN and CS. Test: a UI or label test.
23. **F-028.** After the start boundary moves past the playhead, the position line and readout
    move to the new start. The same applies to the end boundary when the playhead lies beyond it.
    Test: a component or state test.
24. **F-130.** When the editor row wraps, Previous and Next stay together. Play, Reset, Discard and
    Accept form the second group. At 1024 px, groups have at least 8 dp between them. Test: a UI
    test at 960 px that Previous and Next share a row.
25. **F-127.** At 1512 px, text and the main content of calibration and the task screens are capped,
    at about 1100 dp, like Settings. The level meter sits next to the picture. Test: a UI test at
    1512 px checking the content width.
26. **F-095.** `app.log` gets one INFO line each when:
    - a session is created;
    - a take starts and finishes;
    - the session finishes;
    - processing starts, ends or fails.

    Each line carries the session ID only, never a patient code or path (the F-059 rule). Test: a
    log-capture test, plus the existing redaction test.

### Both

27. **Suites green after each increment:**
    - web: backend `npm test`, frontend `npm test`, `npm run lint` (no new problems) and
      `npm run test:e2e`;
    - desktop: `./gradlew.bat :app:test`, counting the result XMLs.
28. **Red checks.** Each new test fails on the old code. I check this myself.
29. **Re-tests.**
    - **Run 15:** the web, against the container, after you OK the deployment. It covers
      criteria 1–13 live, as the right personas.
    - **Run 16:** the desktop, via hot-reload. It covers criteria 14–26.
    - Neither run may find a new major or minor finding.

## Verification plan

- I review every diff.
- I check each red test myself, by restoring the old file for one run.
- Suite counts are recorded per increment.
- An independent `/verify` critic runs before any commit.
- Runs 15 and 16 produce reports with screenshots.
- A leak scan and a link check run before any docs commit.

## Assumptions (veto any)

- **F-090:** the desktop's server address is compiled in, so the panel's address is for people to
  read only. Showing the resolved absolute URL is enough.
- **F-127:** a cap of about 1100 dp, the same as Settings. The session list height is not changed.
- **F-133:** the reserved line stays empty while the camera works. It costs about 20 dp at
  960x720; if it breaks the minimum-window layout, the message moves into the picture area instead.
- **F-095:** logging goes at INFO level in the existing `app.log` setup. There is no new log file.
- **F-025:** the label counts repetitions within the task (one-based) and doesn't show take
  numbers.
- **Config:** no change to the served config or the database.

## Key decisions

- **F-078:** redirect instead of showing an "already signed in" page; the redirect is less UI.
- **F-112:** use the protocol or catalogue label instead of only stripping the `_opt` suffix. The
  fallback is kept for records without a label.

## Increments

Each increment is one Sonnet engineer. Its checkpoint is my diff review, the red check and green
suites.

### Web batch

| # | Content |
|---|---|
| W1 | Texts and i18n: F-070, F-084, F-098, F-101, F-115 |
| W2 | Forms and layout: F-071, F-082, F-099 |
| W3 | Data and routing: F-090, F-112, F-078, F-109 web tile |
| W4 | API: F-100 |

### Desktop batch

| # | Content | Engineer |
|---|---|---|
| D1 | Small UI: F-064, F-088, F-089, F-105, F-046 | `ui-engineer` |
| D2 | Questionnaire: F-065 | `ui-engineer` |
| D3 | Editor: F-025, F-028, F-130 | `ui-engineer` |
| D4 | Layout: F-133, F-127 | `ui-engineer` |
| D5 | Records and logs: F-109, F-095 | `domain-engineer` |

### After the batches

1. **Record (me):** update `findings.md` and the reports.
2. **Deploy:** ask you, then deploy the web to the container per `docs/container_deployment.md`.
3. **Re-test:** run 15 (web) and run 16 (desktop). I save the reports.
4. **Check and commit:** the `/verify` critic, then commits only when you ask.

## Out of scope

- group D;
- the open re-runs F-034, F-055, F-062, F-066 and F-134 to F-136. Run 16 may pick them up if
  there is time, as an optional extra;
- the full restyle of the questionnaire modal.
