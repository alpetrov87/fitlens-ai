# Workout Builder / Тренировки

Branch: `feature/workout-builder-garmin`, based on default branch `main` at
`e6dd7b45cef091e6f925b7e1597bcd60ae60e821`. No automatic merge or deployment.

## Delivery and verification boundaries

This version implements the offline builder, two editable templates, private
persistence, JSON import/export/sharing, and a Garmin conversion/provider
boundary. **Real Garmin upload is blocked by missing trusted authentication
infrastructure and a live authenticated test environment.** The Android Garmin
screen says setup is required and disables upload. Payload preview does not
make network requests. No Garmin account password is requested or stored.

The existing Personal API activity/history/nutrition integration is unchanged.
Direct MFP features from `experiment/mfp-direct-diary` are not merged into this
branch: it starts from main as requested. Existing main features remain intact.

No screenshots or on-device UI results are claimed; no emulator, authorized
Android device, Garmin account or Enduro 3 is available in this environment.
The final delivery report distinguishes local checks, CI and live-device tests.

## Architecture

- `workouts/Workout.kt`: Garmin-independent immutable model, validation,
  duration calculation, duplication and future target types.
- `workouts/WorkoutJson.kt`: versioned portable JSON, input size/depth checks.
- `workouts/WorkoutRepository.kt`: CRUD, safe import, idempotent seeding and
  versioned storage behind a small testable storage interface.
- `workouts/WorkoutTemplates.kt`: A/B routines and exercise instructions.
- `workouts/GarminWorkoutProvider.kt`: compatibility, explicit mapping and
  payload conversion; a separate transport contract and sync coordinator.
- `ui/WorkoutsScreen.kt`: library, detail, recursive editor, import/export,
  confirmation dialogs and Garmin setup/preview screen.

The project uses private SharedPreferences JSON stores for its calendar,
imports and profiles, not Room or a backend. Workouts reuse that architecture
rather than adding a database dependency or rewriting existing repositories.
A complete library is committed in one synchronous SharedPreferences transaction.
Existing health/calendar stores and Garmin keys are not migrated or cleared.

The fifth top-level navigation item is **Тренировки / Workouts**. Layout and
colors use the existing Material 3 theme and bear branding. Bulgarian and English
UI resources are included. Template exercise names are English; their persisted
instructions and safety guidance are Bulgarian, as requested.

## Templates

Both templates are immediately seeded once and are fully editable. A duplicate
becomes a user-created workout with new workout/step IDs and no Garmin metadata.
Deleted templates do not reappear on the next launch; seeding never overwrites
saved edits. The seeded-ID markers are committed with the library.

| Part | Strength A (Tuesday) | Strength B (Thursday) |
| --- | --- | --- |
| Warm-up | March in Place, Arm Circles, Hip Circles, Shallow Squats | Same four individual steps |
| Warm-up timing | 4 × 30 seconds = 120 seconds | 4 × 30 seconds = 120 seconds |
| Main exercises | Squat; Incline Push-up; Resistance Band Row; Reverse Lunge; Calf Raise; Resistance Band Pull Apart | Sit to Stand; Resistance Band Chest Press; Resistance Band Row; Glute Bridge; Standing Hip Abduction; Calf Raise |
| Per exercise | 40 seconds work + 20 seconds rest | 40 seconds work + 20 seconds rest |
| Main block | 2 × 360 seconds = 720 seconds | 2 × 360 seconds = 720 seconds |
| Total | **840 seconds / 14:00** | **840 seconds / 14:00** |

The final 20-second rest is retained in both rounds; no cooldown is added by
stealth. No dumbbells or plank steps are included. Instructions emphasize
controlled movement, breathing, avoiding excessive strain, stable supports,
secured bands and stopping if pain occurs. They do not diagnose conditions or
guarantee individual medical safety.

## Editing and duration

Library cards show sport, duration, exercise/round counts, template status and
sync state. Search by name and filter by sport. Delete requires confirmation.

Open a workout, choose Edit and expand a step. Choose an exercise from the
catalog or rename it as a custom exercise. Edit instructions, step type,
duration condition/value, rest and repeat count. Move steps with the up/down
buttons. Add exercises, repeat groups, individual warm-up or cooldown steps.
Warm-up additions go before the existing steps; cooldown goes at the end.
Save is disabled while required values are invalid. Back/Cancel asks before
discarding the draft. Saved workouts survive application restart. Unsaved
editor drafts are not guaranteed to survive process death or configuration
changes; save before leaving the screen.

Time-based steps and rests contribute seconds; repeat groups multiply all
children including the final rest. Rep/distance/lap steps have unknown elapsed
time, so the UI explicitly shows a lower bound, not an invented exact duration.
For repetition-based payloads, the required Garmin `estimatedDurationInSecs`
metadata uses 0 as an unknown-duration sentinel, consistent with upstream
strength examples; this is not a claim of zero elapsed workout time.
Those conditions, future heart-rate/pace/power targets and additional local
sports are modeled independently of Garmin support. The UI currently edits
step duration conditions but not target zones. Strength time/reps conversion is
implemented; other sports, distance/lap/targets and nested Garmin repeat groups
are rejected by the Garmin adapter. Local nested groups are supported to a
bounded depth. Limits include 200 stored step nodes per workout, 200 workouts,
1–50 repeats, positive values, at most 3600 seconds per timed step/rest and at
most 24 hours of calculated time. There is no exercise timer/player in this MVP.

## Portable JSON schema v1

Top-level fields:

- `schemaVersion: 1`, UUID `id`, `name`, `description`, `sport`, `equipment`.
- `isTemplate`, millisecond `createdAt` and `modifiedAt`.
- Ordered `steps` array.

Each step has UUID `id`, `kind`, `name`, `exerciseKey`, `instructions`,
`durationKind`, integer `value`, `restSeconds`, `iterations`, `children` and
optional `target: {kind, minimum, maximum}`. Step kinds are WARMUP, EXERCISE,
REST, RECOVERY, COOLDOWN and REPEAT. Duration kinds are TIME (seconds), REPS,
DISTANCE (metres) and LAP (the value is reserved, not elapsed time).

Export a template from the app for a complete valid example. JSON restores the
full ordered group structure and instructions. Import uses Android's document
picker, checks size (500 KB), nesting, schema version, enums and model validity,
then imports a **new local copy**, never overwriting an existing workout or
trusting remote IDs from an external file. Export and sharing exclude Garmin
IDs, synchronization errors, pending request keys and credentials. Imported
text is not executed. Share uses a non-exported FileProvider restricted to a
workout cache directory. User-chosen exported files remain under user control.

**Garmin Connect does not necessarily accept this JSON.** It is bAIr's portable
format, not Garmin's Import Data format. FIT export was not implemented: a valid
FIT activity is not a structured FIT workout and would not establish Connect
workout import support. No FIT checksum/parser validation is claimed.

## Persistence and migrations

Private preferences `bair_workouts`, key `library`, contain storageVersion 1:
`workouts` (portable content plus private sync metadata) and `seeded` template
IDs. The adapter checks commits; failures surface in the UI. Invalid or future
storage versions are rejected without clearing/replacing their bytes. A defined
legacy storageVersion 0 (an array of portable workouts) is read and rewritten
as v1 on the next successful mutation. This is tested with an in-memory storage
adapter; main previously had no workout database to migrate. No Android Room
migration tests are applicable. Android disk-backed persistence and document
picker behavior still need the phone checks below. Uninstalling the test app
removes its private library; export files first if needed.

## Garmin research (2026-10-29)

Inspected upstream [python-garminconnect](https://github.com/cyberjunky/python-garminconnect)
commit [`3eee7a4db6c2242967aae1780b8839599ea7dcbb`](https://github.com/cyberjunky/python-garminconnect/tree/3eee7a4db6c2242967aae1780b8839599ea7dcbb):

- `garminconnect/api/workouts.py`: `upload_workout` POSTs JSON to
  `/workout-service/workout`; `update_workout` PUTs the full body to
  `/workout-service/workout/{id}`. `upload_strength_workout` accepts its typed
  StrengthWorkout model. These are separate from recorded activity upload.
- `garminconnect/workout.py`: strength sport ID 5; ExecutableStepDTO,
  RepeatGroupDTO, warm-up, time/reps, rest, repeat helpers and optional notes.
- `garminconnect/exercises.py`: verified category/name tuples used below.
- `tests/test_strength_workout.py`, `tests/test_garmin_unit.py`,
  `tests/test_package_layout.py` and `test_data/sample_strength_workout.py`:
  examples/assertions for structured uploads, updates and timed strength steps.
- `garminconnect/api/base.py`: username/password sign-in, token/session restore,
  expiry handling and an MFA continuation state. This is unofficial Garmin SSO,
  not official OAuth. Android does not embed or depend on this Python library.

The live official Personal Swagger fetch returned proxy HTTP 403 in this
execution environment. Personal API workout-write support was therefore **not
verified**. The existing adapter only supplies activity/health reading flows;
it is not repurposed into an unverified workout writer. Upstream source and
mock tests do not prove current Garmin server acceptance or Enduro 3 behavior.

### Exercise mappings

| bAIr exercise | Garmin category / exerciseName | Policy |
| --- | --- | --- |
| Squat | SQUAT / AIR_SQUAT | Exact bodyweight variant |
| Incline Push-up | PUSH_UP / INCLINE_PUSH_UP | Exact |
| Band Row | BANDED_EXERCISES / ROW | Exact |
| Calf Raise | CALF_RAISE / CALF_RAISE | Exact |
| Band Pull Apart | BANDED_EXERCISES / PULL_APART | Exact |
| Band Chest Press | PUSH_UP / CHEST_PRESS_WITH_BAND | Exact catalog tuple |
| Standing Hip Abduction | HIP_STABILITY / STANDING_HIP_ABDUCTION | Exact |
| Arm Circles | WARM_UP / ARM_CIRCLES | Exact |
| Hip Circles | HIP_STABILITY / HIP_CIRCLES | Exact |
| March, shallow squat, reverse lunge, sit-to-stand, glute bridge, custom | No asserted exact mapping | Explicit generic-step consent required |

The generic fallback omits category/name and keeps the original name and
instructions in the step description. It never substitutes a weighted,
unrelated, or harder exercise. Its actual Garmin acceptance is unverified.
Every fallback is listed before preview and requires an explicit checkbox.
Instructions remain stored locally irrespective of Garmin support.

The two templates convert into four timed warm-ups followed by a repeat group
with 12 children (six timed exercises and their six rests), repeated twice.
`skipLastRestStep=false` preserves 14 minutes. Preview is clearly labelled as
conversion only. Successful Connect upload would still not mean watch delivery.

### Provider, synchronization and duplicate protection

`WorkoutSyncProvider` exposes connection/setup state, connect/disconnect,
compatibility and conversion. `WorkoutUploadTransport` is the future trusted
backend boundary; there is **no configured production transport** in this MVP.
`WorkoutSyncCoordinator` is tested with fake responses, not called by the setup
screen. It persists attempts, pending fingerprints, errors and returned positive
workout IDs. It supports explicit CREATE_NEW and UPDATE_EXISTING modes; no edit
triggers an automatic upload. Same-content confirmed uploads return the stored
result rather than posting again. Requests are serialized across coordinator
instances. Unknown network outcomes or invalid responses retain a pending
fingerprint and block retries until remotely reconciled, preventing blind
re-posting after a timeout. There is no remote reconciliation UI yet. A trusted
backend must implement reconciliation/idempotency before production enablement;
Garmin itself is not assumed to support bAIr's request key.

401/expired-session/rate-limit/validation errors have typed failure codes;
unknown failures do not expose raw server messages or credentials. Backend
implementations must translate only confirmed rejection responses into retryable
codes. Private metadata also distinguishes LOCAL_ONLY, READY_TO_SYNC, SYNCING,
SYNCED_TO_GARMIN, SYNC_FAILED and UNSUPPORTED. A restored unfinished attempt is
treated conservatively as failed/pending rather than asserted as uploaded.

### Authentication and hosting decision

No server/deployment configuration exists in this repository. No available host,
TLS endpoint, credential vault, device account or authentication approval can be
inferred. Consequently a real Garmin sign-in/upload cannot safely be configured
or tested here. Do not send Garmin credentials to Codex or ChatGPT.

| Option | Evaluation |
| --- | --- |
| Small backend service | Preferred production path: authenticated app sessions, TLS, encrypted Garmin sessions at rest, secret manager, rate limits, auditing without credential/body logs, MFA/expiry handling, per-request authorization and idempotency reconciliation. Requires a separate reviewed security design and a selected host. |
| User-owned Proxmox | Suitable personal hosting option after setup: isolated service/container, restricted account, persistent encrypted storage, TLS/VPN, backups and updates. Proxmox alone does not provide application authentication or a secret vault. No server is contacted or deployed by this task. |
| GitHub Actions proof of concept | Only a manually authorized, trusted-code experiment with narrowly scoped secrets and no untrusted PR code execution. Not an app backend, session store or unauthenticated dispatch endpoint. No credentialed upload workflow is added. |

A future setup must keep Garmin password entry/session issuance on the trusted
backend, complete genuine Garmin MFA when requested, return only short-lived
app-scoped tokens to Android (protected by Keystore), support expiry/revocation
and delete credentials/sessions on disconnect. Never bypass MFA or CAPTCHA.
A public multi-user credential store is outside this MVP. Unofficial endpoints,
exercise catalogs and SSO may change or be restricted by Garmin; no guaranteed
service compatibility is claimed.

## Build, APK and test signing

```sh
BAIR_WORKOUT_TEST_BUILD=true ./gradlew --no-daemon \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Use JDK 17/21 and the Android SDK configuration described in README. The test
build has ID `com.fitlens.ai.workoutstest`, label **bAIr Workouts Test**, and a
development debug key. It installs alongside the existing app, not over it;
private Garmin credentials/imports are intentionally not copied between apps.
The test build ignores persistent signing-key environment variables. The
feature branch and its PR skip the persistent-signing CI step entirely. No
production signing secrets are used for this feature APK. Debug certificates
may change between runners; updating a previous test APK may require uninstall
of that separate test app (export workouts first). Existing bAIr is unaffected.

Actions → **Android debug APK** → successful run for this branch/commit →
artifact `bair-workouts-test-<run>-<sha>` → unzip → `app-debug.apk`.
`BUILD_INFO.txt` contains the exact branch and commit. Artifacts expire after
14 days. CI builds, runs unit tests and lint before artifact upload; no deploy
or merge occurs. A local build alone is not CI success.

## Manual Android / Enduro 3 checklist

1. Install the CI APK and open **bAIr Workouts Test**, then **Тренировки**.
2. Confirm A/B are listed as editable templates, Strength, 14:00, six exercises
   and two rounds. Search/filter the library.
3. Open A. Confirm four separate warm-ups of 30 seconds each; main group has
   six exercises of 40 seconds plus 20 seconds rest, repeated twice.
4. Duplicate A; edit a step, rest, repeat count, descriptions and exercise order.
   Confirm duration changes. Try blank names, zero time or repeat count; Save
   must be disabled. Try a reps-based step and confirm duration is not exact.
5. Save, reopen, restart the app and check persistence. Delete the duplicate
   with confirmation. Edit a template and ensure re-opening does not overwrite it.
6. Export A via the file picker, share the file, and import it again. Confirm a
   separate local copy with independent IDs. Malformed/unknown-version JSON must
   fail without deleting previous workouts. Test offline / airplane mode.
7. Open Garmin setup. Confirm it states **setup required**, upload is disabled,
   unsupported mappings are listed and preview requires generic-step consent.
   Inspect warm-up/time/rest/repeat structure. No account login or upload occurs.
8. Check the other main app sections and both themes/languages.

**After a separately reviewed backend is available**, the remaining acceptance
sequence is: connect with genuine Garmin authentication/MFA; confirm upload
mappings and CREATE/UPDATE choice; send A; record the actual returned workout
ID; inspect the workout in Garmin Connect; select Send to Device in Garmin
Connect and perform its normal Bluetooth sync with Enduro 3; then inspect all
warm-up names, exercise names, times, rests and repeats on the watch. No USB
connection should be necessary for that normal Connect sync path. These are
future verification steps, not steps enabled by the current APK. Only the user
can confirm actual watch behavior here.

Record these separately: (1) automated tests, (2) real API acceptance, (3)
workout visible in Connect, (4) workout visible and correct on Enduro 3.

## Troubleshooting

- **No Garmin upload:** expected setup state, not a broken Personal API key.
- **Generic mapping warning:** exact identifier not verified; no silent exercise
  substitution. Preview does not prove watch animation/recognition support.
- **Unknown duration:** reps, distance or lap steps do not have a known elapsed time.
- **Save disabled:** inspect required names, timing and group limits.
- **Import fails:** export a valid bAIr v1 file; Connect JSON is a different schema.
- **Storage error:** do not clear app data before exporting/backing up; future or
  corrupt versions are intentionally not overwritten by template seeding.
- **Update signature failure:** verify you selected the separate test app APK;
  do not uninstall your normal bAIr installation to install this test build.

## Recorded validation results — 2026-10-29

Implementation commit: `a44f616919db95db931b7d914d9b75eb36ddb014`.

| Check | Result |
| --- | --- |
| Local `assembleDebug` with `BAIR_WORKOUT_TEST_BUILD=true` | Passed |
| Local unit tests (existing tests plus workout tests) | 52 tests, 0 failures/errors |
| Local `lintDebug` | Passed |
| Two generated payloads validated with upstream `StrengthWorkout` | Passed using researched upstream commit and Pydantic 2.13.4; not a server test |
| APK manifest inspected with Android `aapt` | `com.fitlens.ai.workoutstest`, label `bAIr Workouts Test`, version `0.22.0`, min SDK 26 |
| GitHub Actions implementation build | [Run 40946895591 — Success](https://github.com/alpetrov87/fitlens-ai/actions/runs/40946895591) |
| Published artifact for that implementation commit | `bair-workouts-test-129-a44f616919db95db931b7d914d9b75eb36ddb014` |
| Phone UI, Android disk persistence and SAF/share flows | Not manually tested; follow checklist above |
| Real Garmin API upload / visible in Connect / visible on Enduro 3 | Not tested; live upload disabled pending trusted backend |
| Pull request creation | GitHub GraphQL API returned `Forbidden`; branch push succeeded. Use the compare link below to create one PR. |

[Create the review PR targeting main](https://github.com/alpetrov87/fitlens-ai/compare/main...feature/workout-builder-garmin?expand=1).
No PR has been merged and no production deployment was performed. Later
metadata/documentation commits may produce additional artifacts; match their
SHA to `BUILD_INFO.txt` rather than assuming they are the same build.
