# bAIr — Android app

**English** | [Български](README.bg.md)

bAIr brings training, nutrition, race planning, and runner rankings together in
an Android app built with **Kotlin**, **Jetpack Compose**, and **Material 3**.
Originally named FitLens AI, it keeps the application ID `com.fitlens.ai` so
existing installations can be updated without changing their identity.

The app uses a bear-and-trail visual identity, forest green, cream, and warm
orange accents. Light and dark themes follow Android. The interface supports
English and Bulgarian, with an in-app language selector and Android 13+ app
language settings.

This guide describes the code on **main**. Experimental features on other
branches, including direct MyFitnessPal diary access, are not part of main.
The [Bulgarian guide](README.bg.md) also preserves the earlier release notes.

## Features

- **Home:** daily steps, activity, nutrition summaries, and recorded workouts.
- **Garmin Personal API:** connect with your personal API key, sync today's
  metrics, and retrieve up to 30 days of activities, sleep summaries, and HRV
  when Garmin provides them. Today's data refreshes when the app opens and
  approximately every four hours through Android WorkManager. Android may
  defer background work to conserve battery.
- **Nutrition imports:** read Garmin and MyFitnessPal CSV files locally through
  Android's file picker. Main does not sign in to MyFitnessPal or use its
  session cookies.
- **Race calendar:** browse upcoming trail-running and MTB events in Bulgaria
  from [racecalendar.bg](https://racecalendar.bg/), choose a distance, and add it
  to your personal calendar. Events can also be added manually or imported
  from a public event link, then reviewed before saving.
- **My progress:** import available public BTR and ITRA rankings and race results
  and keep them locally for review and inclusion in a training report.
- **ChatGPT reports:** review and share training summaries, recovery data,
  runner-profile information, and the selected race's details. No Gemini or
  OpenAI API key is required. You can also save a ChatGPT project link, copy
  the report, open the project, and paste it into a conversation yourself.

Missing dashboard metrics are shown as zero/no data rather than demo values.
The completeness of external data depends on the source and your account.

## Download a test APK

1. Open [Actions](https://github.com/alpetrov87/fitlens-ai/actions) and select
   **Android debug APK**.
2. Choose a successful run for the branch and commit you want to test.
3. Under **Artifacts**, download `fitlens-ai-debug-<run number>`. GitHub sign-in
   is required to download artifacts.
4. Extract the ZIP archive to find `app-debug.apk`.

Artifacts are retained for **14 days**. Run the workflow again if an artifact
has expired. The workflow builds the debug APK, runs unit tests and Android
Lint, and uploads the artifact only after those checks pass. It runs on pull
requests, pushes to `main` or `feature/**`, and manual **Run workflow** requests.
It does not deploy the app or publish it to Google Play.

## Install or update

Requires **Android 8.0 / API 26 or later**.

Open the extracted APK on your phone. If prompted, allow **Install unknown apps**
for the app opening that file; you can revoke that permission afterward.
Alternatively, enable USB debugging, connect and authorize the phone, and run:

```sh
adb devices
adb install -r app-debug.apk
```

The launcher name is **bAIr**. Open **Settings** to configure Garmin, import CSV
files, or change the app language.

An update must have the same application ID and signing certificate as the
installed app, and an acceptable version code. CI assigns increasing version
codes and supports a persistent signing key through GitHub Secrets. Without
those secrets, debug keys may differ between CI machines. Local debug builds
can also use a different key or a lower version code than CI builds.

If installation reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, check the signing
setup before uninstalling: uninstalling removes local app data. See
[APK signing and required GitHub Secrets](docs/APK-SIGNING.md) for setup and
troubleshooting. These APKs are development builds, not Google Play releases.

## Garmin setup

1. Sign in to Garmin Connect Web and open
   [Personal API keys](https://connect.garmin.com/app/settings/personalApiToken).
2. Create a key, accept Garmin's terms, and select its lifetime.
3. Copy the key when Garmin displays it. Do not put it in an issue, chat,
   screenshot, or commit.
4. In bAIr, open **Settings → Garmin Connect**, paste the key, and connect and
   sync. You can also request today's data or the 30-day training history.

The API key is encrypted locally using Android Keystore. Access tokens are kept
in memory. The adapter uses Garmin's personal-key flow, not a multi-user OAuth
backend. It stores activity summaries, not original FIT/GPX tracks or complete
sensor telemetry. Available fields can include activity type, start time,
duration, distance, calories, steps, heart rate, and elevation gain.

Official references:
[Personal API](https://developer.garmin.com/gc-developer-program/personal/personal-api/),
[FAQ](https://developer.garmin.com/gc-developer-program/personal/personal-faq/),
[Swagger documentation](https://developer.garmin.com/gc-developer-program/personal/swagger-docs/),
and [Garmin's Python example](https://github.com/garmin/connect-personal-api-python).
Personal API access is for the key owner's data and is subject to Garmin's terms.

## CSV imports

Choose **Import Garmin CSV** or **Import MyFitnessPal CSV** in Settings. The app
reads the selected file locally and saves imported summaries in private app
storage. Importing the same provider and date updates that provider's data;
Garmin and MyFitnessPal summaries for a date can be combined.

**Garmin:** one daily summary per row, with a date and at least one supported
metric such as steps, active minutes, or distance.

```csv
Calendar Date,Steps,Active Minutes,Distance (km)
2026-10-22,7840,42,5.6
```

**MyFitnessPal:** dated food or meal rows with at least one supported nutrient.
Rows for the same day are summed.

```csv
Date,Calories,Protein (g),Carbs (g),Fat (g)
2026-10-22,620,35,60,18
2026-10-22,410,28,45,12
```

Supported formats include UTF-8, comma/semicolon/tab delimiters, and ISO,
US `M/D/YYYY`, or Bulgarian `D.MM.YYYY` dates. Main does not import Garmin
FIT/GPX/TCX archives. The clear-imports action removes imported health data;
calendar events are kept separately.

## Calendar and runner profiles

Public event links are read for schema.org Event/SportsEvent data, additional
properties, Open Graph, and labelled values in HTML. The app can extract a
name, date, start time, sport, location, distance, cutoff time, and elevation
gain. Review and correct the fields before saving. Login-protected,
JavaScript-only, or unusually formatted pages may require manual entry.
The race catalogue depends on what organizers publish and may be incomplete.

BTR and ITRA imports read public profile data without signing in to those
accounts. ITRA may use Jina Reader as a fallback when direct access is blocked;
only the public profile URL is sent to that service. See
[runner-profile import details and limitations](docs/runner-profile-import.md).

## ChatGPT and data handling

Reports are created only when requested and shown in full before sharing. The
app does not include Garmin keys, access tokens, email addresses, or passwords
in the report. After you share a report, the receiving service's terms apply.
ChatGPT responses stay in ChatGPT and are not automatically imported into bAIr.

A saved project link opens that project after copying the report. It does not
automatically select a conversation or submit a message. ChatGPT cannot call
back into the app to retrieve phone data; no remote data-access backend or
ChatGPT Action/MCP integration is included.

## Local development

Use Android Studio, JDK **17** or **21**, Android SDK Platform **35**, Build Tools
**34.0.0**, and Platform Tools. The included wrapper downloads **Gradle 8.9**
with checksum verification.

```sh
export ANDROID_HOME=/path/to/android-sdk
# Alternatively, set sdk.dir in your local local.properties file.
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Reports are written to:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

Unit tests and lint do not replace testing the UI and integrations on a device.
Do not commit API keys, keystores, passwords, or local SDK configuration.

## Architecture and contributions

`MainActivity` wires dependencies, `DashboardViewModel` exposes state through
`StateFlow`, and Compose observes it with lifecycle awareness. `HealthRepository`
provides the dashboard; activity and nutrition sources supply its data.
Garmin adapters, CSV import, profile import, and local calendar storage are
separate concerns. WorkManager schedules background Garmin synchronization.

Make changes on a separate branch and open a pull request targeting `main`.
See [main-branch protection](docs/MAIN-PROTECTION.md). Publishing an APK artifact
does not deploy the app.

## Workout Builder (test branch)

`feature/workout-builder-garmin` adds an offline **Workouts** library, editable
14-minute Bair Strength A/B templates, a structured editor, and versioned JSON
import/export/sharing. The dedicated CI APK installs as **bAIr Workouts Test**
alongside the normal app and does not use production signing secrets.

**Garmin upload is not enabled:** the provider/converter and duplicate protection
are mock-tested, but secure backend authentication and live Garmin/Enduro 3
verification are unavailable. The UI clearly shows a setup-required state.
See [implementation, limitations, build and manual test instructions](docs/workout-builder.md).
