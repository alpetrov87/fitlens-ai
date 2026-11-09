# UTMB Index — public profile import (0.24.0)

The existing `experiment/mfp-direct-diary` branch adds UTMB beside BTR and ITRA
in Progress. The saved profile participates in the existing reviewable BG/EN
ChatGPT report. MFP, Garmin synchronization, nutrition and workout branches are
not rewritten or merged.

## Phone setup

1. Install the branch APK from the successful Actions run's Artifacts.
2. Open **Прогрес / Progress → UTMB**.
3. Paste your complete public profile link, for example
   `https://utmb.world/en/runner/1234567.sample.runner` (synthetic example).
4. Choose **Прочети от линка / Read from link**, inspect the values, and save
   if confirmation is shown. Later use **Обнови от профила / Refresh profile**.
5. Compare overall Index and 20K/50K/100K/100M against the website. `—` means
   unavailable, not a zero score. No Running Stones or race-result history is
   inferred from category indexes. Optional existing manual fields remain usable.
6. Prepare a ChatGPT report and inspect the UTMB profile, categories, URL and
   last local update before explicitly sharing. Restart/offline: saved values
   remain available. A failed fetch preserves the previous saved profile.

No user profile URL is seeded or embedded in the APK/source. Each user saves
it locally. These are public profile values, not official OAuth or Garmin data.

## Research and implementation

No self-service official UTMB API contract was verified. An independently
maintained open-source client documents the runner page's embedded Next.js data:
https://github.com/jasonzzx/itra-utmb-index/blob/main/src/lib/utmb.ts

Its documented structure is `__NEXT_DATA__.props.pageProps.performanceIndexes`,
with `piCategory` and numeric `index`. We implement a narrow parser for that
structure rather than searching arbitrary page numbers, recommendations, race
ratings, or runners with the same name. Null/zero categories are unavailable.
Malformed/unsupported pages fail without overwriting saved values. The complete
runner slug is retained; the numeric ID constrains every redirect, and any
canonical/query identity evidence is checked. Redirects to another runner or
login are rejected. The existing public downloader retains HTTPS, DNS/address
checks, redirect limits and body bounds. JSON nesting is capped before parsing.
There is no UTMB password, cookie, API key, backend, challenge bypass, or
third-party reader request for this integration.

The provider's fields fit the existing private SharedPreferences store: adding
UTMB leaves BTR/ITRA keys unchanged. The category grid is derived from the saved
summary, so it remains available offline. Changing a UTMB profile URL clears its
unsaved score/results draft to avoid mixing different runners. Scores are not
compared directly to ITRA or BTR.

## Verification limits

This environment's outbound connection to `utmb.world` (including the submitted
public profile) failed with proxy CONNECT 403. No actual user Index value or
successful real profile import is claimed. The parser/URL checks and bilingual
prompt integration are tested with explicitly synthetic fixtures based on the
above source; API/site access and the latest page schema require the phone test.
The site may change its schema or restrict access. Failures are shown; no
invented values, hidden search fallback, or authentication bypass is used.
