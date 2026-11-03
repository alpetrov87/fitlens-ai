# Garmin metrics experiment — MFP branch, 0.21.4

This read-only test uses the existing encrypted Personal API key. It does not
require a backend, change MFP behavior, overwrite saved metrics, or include its
results in the ChatGPT report.

## Phone test / Проверка

1. Install the APK from the `experiment/mfp-direct-diary` workflow artifact.
2. Open **Настройки → Garmin → Тест на Garmin показатели** (English:
   Settings → Garmin → Test Garmin metrics). Configure your existing Personal API
   key on the phone first if Garmin is not connected. Never send the key in chat.
3. Stay on the Settings screen while it runs. There are at most 12 sequential
   data requests, plus token exchange. The test can take several minutes with
   slow responses; navigating away cancels the coroutine between requests.
4. Inspect the HTTP codes and selected numeric field names/values. You can select
   the text to copy it if you choose; no automatic upload or sharing occurs.
5. Report which sources returned VO₂ max / training effect / load / recovery.
   Review the displayed fitness values before sharing: they are personal data.

HTTP 200 with no matching fields only means the selected numeric extractor did
not find a match. It is not proof of absent Garmin data. HTTP 204 can mean an
empty result. 401/403 indicates access refusal; 429 indicates request limiting.
The probe stops further requests after 401/403/429. HTTP 0 represents a local
network, decoding, or size-limit failure. Raw provider errors are never shown.

## Sources and limits

Verified request paths in Garmin's official example client:
https://github.com/garmin/connect-personal-api-python/blob/main/src/garmin_personal_api/client.py

The probe requests daily summary, fitness age, detailed daily sleep, daily HRV,
7-day stress and Body Battery, and at most five recent activities and their
activity details (30-day search). It does not guess unsupported Training
Readiness / VO₂ max endpoints or use unofficial username/password login.

Only numeric fields whose keys match selected fitness concepts are displayed.
Strings (including names, tokens, status labels), activity IDs, full JSON and
session data are excluded. Nested traversal is capped at depth 10, five array
entries, 100 results per response, and 2 MB per HTTP body. Units and technical
field names retain Garmin's meaning; there is no invented conversion or score.
HTTP redirects and automatic network retries are disabled for the probe.

Results live only in Compose state until leaving the screen/process. No report,
response file, logs, preferences or analytics are written. Existing Personal API
key handling is unchanged. A successful mocked/unit test or APK build does not
verify real API availability: the phone test with the user's own account is
required. This experiment intentionally does not implement persistent metric
history or claim Training Readiness/VO₂ max support before response validation.

## 0.21.5 — extended test and one-tap copy

Supersedes the earlier limits above: sleep and HRV summaries cover 30 days;
activity search covers 90 days, with the latest 50 results requested and up to
12 detail requests prioritizing running/cycling. This does not promise all
activities in that period or a VO₂ max measurement. There are at most 21 data
requests plus token exchange. Existing access/rate-limit stopping rules apply.

The numeric extractor now reads up to 30 entries per array and 1000 matching
fields per response. ISO dates in records appear in paths, and generic values
inside recognized fitness containers are included. Technical index fields are
excluded. Where `bodyBatteryValuesArray` contains timestamp/level pairs, the
report summarizes minimum, maximum and last valid level from up to 3000 pairs;
missing/sentinel readings are excluded. Last means server array order, not a
verified current Body Battery level. Event impacts remain separate fields.

Use **Копирай целия резултат / Copy full report** in the result dialog. The
clipboard report uses section headings and line breaks, omits the introductory
help paragraph, and is capped at 120,000 characters. Dates and numeric fitness
values are personal data; copying is explicit. Android 13+ receives the sensitive
clipboard flag. This does not prevent another application from reading content
you choose to paste. The app does not automatically share or persist the report.

## 0.22.0 — normal synchronization replaces the probe

The test button and test UI have been removed. Settings' normal Garmin sync
(including scheduled/launch sync) now requests 30-day sleep/HRV, stress and Body
Battery, plus fitness age. The saved typed/allowlisted numeric metrics appear in
Progress → Garmin Recovery, with latest available dates and expandable history.
Activity training effect/load/Body Battery changes are saved from activity lists
and shown on activity cards. Use 30-day history sync to backfill activities.

The existing private SharedPreferences JSON gains an optional `wellness` map on
recovery days and activities. Missing maps load as empty; old records remain
compatible. Partial recovery responses merge with previous fields/days rather
than deleting measurements. Disconnect/clear still deletes Garmin data.

The reviewable BG/English ChatGPT report now includes dated daily metrics and
per-activity training effect/load. Normal sharing confirmation remains in place.
No VO₂ max, readiness score, acute load or medical interpretation is invented.
Body Battery uses the valid reading with latest timestamp (not array order),
and is explicitly historical. Fitness age is dated by retrieval, not an asserted
measurement date. Ambiguous averageSpO2HRSleep is deliberately excluded.

Phone check: normal sync → Progress → Garmin card and history → prepare ChatGPT
report and inspect the added metrics before sharing. Check offline after restart.
Real server schemas beyond the user-provided diagnostic samples still require
phone verification. Existing MFP features are unchanged.
