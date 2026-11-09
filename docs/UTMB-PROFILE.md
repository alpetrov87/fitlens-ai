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

## 0.24.1 — experimental manual website sign-in and read

The user confirmed that their UTMB website account displays category indexes
and per-race data after signing in. No official authenticated API contract or
SSO implementation was verified from this environment. We therefore do not
invent an OAuth client, scrape passwords, replay cookies through a backend, or
promise background authenticated synchronization.

UTMB refresh actions now open a dedicated in-app website screen. Enter the
complete profile link in Progress → UTMB, choose **UTMB · Вход и прочит**, use
the website's own sign-in button, and complete normal verification there.
Press **Моят профил**, open the desired results, then **Прочети отворения
профил**. Inspect the preview and confirm the runner/values before saving.
The report is the existing saved UTMB profile, automatically included in the
reviewable ChatGPT prompt. On returning, Progress reloads the saved profile.

The default Android WebView session stays in the app's private browser storage;
CookieManager.flush persists website sessions, not passwords collected by bAIr.
The app never exports cookies/tokens or injects a native JavaScript interface.
MFP cookies are not cleared. Sign out through UTMB's website; no blanket
CookieManager clear is used. Only HTTPS main-frame navigation is accepted;
file/content access and mixed content are disabled, TLS errors are cancelled,
third-party cookies remain disabled. The actual host is displayed throughout
sign-in. SSO may use other HTTPS domains as a normal website browser would.
Read actions are disabled outside the exact UTMB runner ID, including all
sign-in origins; the page URL is rechecked after the JS callback.

The fixed JS reads selected numeric `performanceIndexes` from page state and
strict visible category-label/score pairs. It returns no full HTML, form values,
passwords, cookies, localStorage or OAuth state. Per-race extraction is limited
to visible UTMB race links whose bounded ancestor row has a date/year, finish
time and a UTMB score/index label (including table headings). Unsupported card
layouts, hidden tooltips, pagination, or unloaded tabs can return no rows. The
preview reports only the number actually read. At most 30 rows and 4000 stored
characters are retained. This is not an exhaustive account export. Missing
categories/results can retain earlier cached data for the same runner only;
the preview shows the resulting combined values and explains this behavior.
Changing runner never transfers cached data to the new runner.

No CAPTCHA/MFA/SSO restrictions are bypassed. Providers such as Google can
refuse embedded-browser sign-in; use another normal website sign-in method if
offered. There is no token-copy workaround. Real login, authenticated category
access and per-race import remain unverified until the user's phone test.
Automated tests use synthetic snapshots and validate origin/ID checks, limits,
field selection and conservative cache handling. JS syntax/fixtures are also
checked locally. Do not send login passwords, tokens or MFA codes in chat.

## 0.24.2 — blank sign-in page troubleshooting

The user reported that bAIr controls remained visible while website content
became white. That does not prove a protection challenge: scripts, cookies or
popup handling can cause the same symptom. This release adds a visible in-app
child window for a user-initiated website sign-in popup, preserving normal
window/opener behavior instead of redirecting it into the parent or losing it.
One popup is allowed; automatic or additional popups are rejected with a message.
HTTPS-only navigation and cancelled TLS errors apply to child windows as well.
Profile extraction remains disabled while the popup is open.

**Помощ при вход / Sign-in help** appears in the main and popup toolbars. It
provides reload, optional third-party cookies limited to these UTMB WebViews,
external-browser comparison, and a copyable safe diagnostic report. This option
is off by default, is not set globally and does not affect the MFP WebView.
Changing it requires reloading the website. Existing website cookies may remain;
use the website logout to end an account session. No blanket cookie deletion or
credential export is performed.

Diagnostics retain at most 20 enum event names, numeric status/error codes and
hostnames. Paths, userinfo, query/fragment values, OAuth codes, console text,
HTML, input values and cookies are never included. JS console errors are counted
as fixed events without capturing their messages. A delayed blank-page probe
returns only a boolean from body-length/control-count checks, never page text.
This is a heuristic, not proof of a block. Update Android System WebView/Chrome,
try the site sign-in again, then copy diagnostics if it still fails.

Opening the original public profile in the system browser is for comparison;
browser cookies/sign-in cannot be imported into bAIr. No user-agent spoofing,
TLS bypass, CAPTCHA solving, MFA bypass or anti-bot workaround is added. Real
UTMB SSO/popup behavior remains a phone test. Synthetic unit tests verify that
copied diagnostics omit sensitive URL components and bound the event history.

## 0.25.0 — styled Index and per-race scores

The user confirmed that website sign-in works on their phone after 0.24.2.
This release keeps that login/popup/cookie flow and changes only extraction,
result presentation and conservative local merging.

Progress now displays a navy/cyan overall UTMB Index card and 20K/50K/100K/100M
category tiles with yellow/orange/green/red accents. Missing values show an
unavailable state, never a manufactured zero. The layout adapts the supplied
wide desktop reference to a full-width hero plus a two-column phone grid.

From the selected signed-in profile, the read script inspects visible official
UTMB race links and their bounded row ancestors. A per-race score must come
from an explicitly labeled UTMB Score/Index table column, aligned div header,
visible score label or accessibility label. It is never guessed from ranking,
finish time, category Index, the overall Index, or an arbitrary last number.
Duplicate links to the same race do not cause the row to be discarded. Locked
or unrecognized scores remain unknown; race title, read date/year, duration,
distance and the official race URL are retained where available. No fetch of
other runners or extraction from a race winner row is performed.

Each result has a separate horizontal card, like BTR/ITRA, with a prominent
**UTMB Score** badge and a link to its race page. New structured browser fields
are validated then stored as readable lines in the existing profile results
field, keeping old records compatible and ChatGPT reports readable. The same
saved rows are included in the existing reviewed prompt, preserving the
semantic distinction between overall/category Index and per-race Score.

Repeat/paginated reads merge by official race URL. A missing per-race score
cannot replace a previously saved numeric score. The preview shows the merged
result before confirmation. The existing limits remain: at most 30 stored rows
and 4000 characters, with newer imported rows preferred when space is limited.
This is a bounded snapshot, not a guarantee of full account history. Open each
page/tab in the website and read it before saving if results are paginated.

Tests use synthetic browser DOM fixtures (table headers, duplicate anchors,
explicit score cards, locked values and off-site links), Kotlin field validation,
cache merging, category parsing and prompt inclusion. The improved per-race
selectors and visual appearance still require a phone test with the actual
signed-in UTMB profile. If a layout is unsupported, it must not invent a score.

## 0.25.1 — results below charts and non-link tables

The user confirmed that even reading while the race list is visible returned
only the overall Index. That points to unsupported row markup, not just lazy
loading. The extractor now supports semantic tables/ARIA grids and ordinary
responsive div grids with Date, Race and UTMB Score headers. A race name may be
plain text or a button; a URL is no longer mandatory when those strong column
labels identify the result. Unlinked rows merge by date, race title and distance.
Arbitrary unlabeled numbers, rankings and the overall Index are still excluded.

If the first explicit read finds no races, the app looks for a Results heading
and makes up to 12 bounded page scroll/read passes, waiting between them for
lazy-loaded content. Nested scroll containers are supported when the WebView
itself cannot page down. The user can stop the operation; navigating or leaving
the selected profile cancels it. Read snapshots are merged before preview to
retain rows from virtualized content. There are no new private API calls,
automated account clicks or authentication changes.

A zero-race result now shows an explicit warning instead of implying that the
overall Index is a complete import. Counts of found race links and table rows
are displayed and included as numeric diagnostic events. No page HTML, login
text or credential values are copied. Open any required Results tab/pagination
manually, then read again. Existing saved results survive failed/empty reads.

Tests cover plain-cell tables, div grids without links, unknown scores, race
identity merging and bounded diagnostics. Actual UTMB DOM variants still require
a phone check; if unsupported, report the displayed counts and a screenshot of
the race section, without sign-in codes or cookies. No extraction success for
the newly reported layout is claimed before that test.
