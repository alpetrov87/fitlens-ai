# MyFitnessPal direct diary experiment — 0.18.0

Branch `experiment/mfp-direct-diary` is based on `feature/garmin-nutrition-details` so existing nutrition testing is retained. No merge or production publishing is performed. This is a manual connectivity/parser experiment, not a working official MFP API integration or background synchronization.

## Phone test

1. Install the GitHub Actions artifact for this branch, signed with the configured repository key.
2. Open Settings → MyFitnessPal → MyFitnessPal — direct access test.
3. Tap Sign in. The actual MyFitnessPal website opens in Android WebView; enter credentials only there. The displayed HTTPS host must be MyFitnessPal. This prototype supports the website's own login, not external Google/Apple OAuth. Complete normal site prompts yourself; no challenge is bypassed.
4. Enter your MyFitnessPal **username**, not email, and a date in YYYY-MM-DD. Tap Open day. Wait until the actual diary is visible. For this prototype English site headings are preferred (common Bulgarian headings are also recognized).
5. Tap Read this day. A diary table is located by meal markers or diary identity/nutrient headings, and only its visible row/cell text and structural classes are extracted. The parser reads food rows, meal headings and the daily total row; goal/remaining rows and meal subtotals are not added to intake.
6. Verify foods, account and date against the website. The URL date is checked automatically; the actual data date cannot be independently guaranteed from this legacy table, so explicit confirmation is required before saving.
7. Save test snapshot stores only parsed data in private local preferences (`mfp_experiment`, one last snapshot). It does **not** update home nutrition, Garmin records, or ChatGPT reports yet. Cancel keeps the result only in memory. Saving a later snapshot replaces the previous one.
8. Sign out/clear removes the WebView cookies/site storage/cache and the saved prototype snapshot. Close alone does not sign out. Website sessions may persist across app restarts until cleared; no guarantee of purely in-memory authentication is made.

## Boundaries

The Activity is not exported. Top-level navigation allows only HTTPS MyFitnessPal hosts; file/content access, mixed content, third-party cookies and additional windows are disabled. Normal HTTPS third-party site resources may still load to support the website. Default certificate errors are not bypassed. No JavaScript interface, native cookie extraction, native login POST or password field is implemented. JavaScript is evaluated only on a matching diary URL/date after the user explicitly asks; it returns only the diary table, capped at 250,000 characters. Login HTML, browser storage and cookies are not captured or logged by native code. Parsed food names/nutrient values are the only saved contents, with date. No health data is sent to GitHub or a new server.

## Expected failures

CAPTCHA, embedded-browser restrictions, account sign-in method or the site's current JavaScript layout may prevent this approach. The parser follows the legacy table structure also used by the open-source `coddingtonbear/python-myfitnesspal` client. A redesigned diary is intentionally reported as unsupported instead of returning fabricated zeros. Missing/unsupported columns are omitted, not inferred. No claim of successful live authenticated access is made until the owner tests with their account. Do not paste session cookies/passwords or private diary HTML into public issues; a redacted screenshot of an error is sufficient.

## Validation

Synthetic parser tests cover food rows, thousands separators, decimal macros, zero versus missing values, daily total versus goal/remaining/subtotal rows, trusted origin/date guards, and rejecting a login form. Android compile, unit tests and lint are run before branch publication. These tests establish the prototype behavior, not compatibility with the live authenticated site.

## 0.18.1 — blank-screen diagnostics

The experiment now owns a Material Surface background/content color, including dark mode, and opens MyFitnessPal sign-in automatically. Native controls are limited to a scrollable 240dp header so they cannot consume the entire portrait screen. Scroll that header to reach date/username/read controls. A reload action, progress indicator, slow-load notice and main-frame HTTP/network/TLS error messages are provided. TLS errors remain cancelled. This fixes initialization/layout visibility problems; it does not prove that MyFitnessPal permits embedded sign-in. If the site still fails, report the displayed status or a screenshot without credentials.

## 0.18.2 — native WebView scrolling and separate controls

The owner's clarification is that the **website itself** cannot scroll, not just the native controls. The WebView now explicitly fills its bounded area, enables native nested scrolling and scrollbars, uses a wide viewport/overview with pinch zoom, and asks the parent not to intercept its touch gesture. The touch listener returns false so native website interaction remains responsible for scrolling/clicking. No CSS is injected into the login page and no website challenge is bypassed.

The old 240dp scrolling header from 0.18.1 is replaced with Website / Day and reading panels. Website contains only the web surface and an always-reachable Read this day action. Day and reading is a separate full-height native scrolling form; Open day returns to Website. Keyboard resizing is enabled. This also removes nested scrolling competition around the WebView.

Use Day and reading → Test WebView scrolling to load an inert local page with 25 numbered sections and a bottom marker in the **same WebView**. It has an opaque local origin, no network resources, forms, scripts or health data, and cannot be imported as a diary. The UI labels it as a local test rather than showing a MyFitnessPal origin. If this page scrolls but MyFitnessPal does not, the remaining problem is specific to the website (for example an overlay or document-level scroll lock). If neither page scrolls, the native interaction/layout still requires device investigation. Sign in or Reload returns from the test to the real site without exporting cookies.

Actual touch behavior and MyFitnessPal compatibility are not verified by JVM tests; they require the phone test. Preserve a redacted screenshot and describe which page is affected rather than sharing credentials/session data.


## 0.18.3 — visible diary table parsing

The owner supplied a screenshot showing successful authentication and a real diary with nutrient labels split over name/unit lines (Calories/kcal, Protein/g, etc.). The parser now normalizes those units, supports styled food rows and semantic headers when meal CSS classes change, and retains the separation between intake, goals, remaining and subtotals. Synthetic regression fixtures reproduce these structures without copying the owner's personal food diary.

Extraction sanitizes the selected table to direct row/cell text and row classes rather than forwarding event handlers, links, hidden fields or all original markup. This also reduces the chance that the 250k capture limit rejects a table with bulky editing markup. Fallback table selection remains restricted to a trusted diary URL/date and recognizable diary markers. On failure only captured character/row counts are shown, not private diary content. This is not an authenticated live-compatibility test: the owner still needs to test the new APK.

## 0.18.4 — partial/current days and explicit page date

A title-only empty meal header no longer replaces known nutrient columns with nulls. Headers are pre-scanned (including repeated footer headers), so unfilled later meals and recorded zero totals can be parsed without inventing missing foods. Synthetic tests cover these partial and empty-day layouts.

The website panel now displays the native selected date. The sanitizer additionally copies only the known diary calendar field `input#date_selector[name=hidden_date_selector]` into a `data-diary-date` table attribute, if it contains an ISO date. This is a calendar field, not a login/hidden credential field. Conflicting URL/document dates are rejected. A diary URL without a date parameter is accepted only when that explicit document date matches the selected day; it is never assumed to mean today. Existing authenticated origin and diary-path checks remain mandatory. Save still requires user confirmation of date/account.

If food rows are absent but numeric totals exist, the preview says only totals were recognized. These fixes are regression-tested locally, but the user's current-day page still needs real-account validation.

## 0.18.5 — meal completeness

The user observed only breakfast in a successful preview. Extraction now considers all diary meal tables and explicit daily total tables in document order, not only the first matching table. Direct rows are copied once per table to avoid nested-table duplication. Plain standard meal names and explicit single-cell heading markers are supported in addition to nutrient/class headers. The parser retains nutrient column alignment across title-only later sections. No totals are synthesized from partial food lists.

The preview reports recognized meal and food counts so the owner can compare the result against the complete page. Synthetic regression data covers Breakfast, Lunch, a custom After training heading, Dinner, styled rows and a separate total. This does not establish that every live layout is covered; 30-day collection remains postponed until completeness is confirmed on the phone.

The review dialog also lists each recognized meal as a compact expandable group, with meal/food counts before the detailed food rows. This prevents a long breakfast list from visually hiding all later meal headings. The stored snapshot still contains all parsed food rows, regardless of which group is expanded.

## 0.19.0: foreground 30-day import

After signing in, open **Day and read**, enter your own diary username and the
last date, then choose **Read and save 30 days**. Confirm the account. The app
visits that date and the previous 29 sequentially, with a pause between pages.
Keep this screen open; Stop or closing it cancels the job. Each successful read
is immediately committed locally by account/date, including all meal rows and
returned daily totals. A new successful read replaces that date; failures do
not clear existing history. Authentication, HTTP, timeout, parsing, or date
errors stop the batch, preserving earlier days. An unrecognized empty diary is
not assumed to be a zero-intake day. Retry the remaining period by selecting
its last unread date. Re-reading a date does not duplicate it.

**Saved history** previews daily totals for the current 30-day period of the
last imported account. ChatGPT report preparation includes these same daily
totals, coverage, and meal/food counts, never cookies or credentials. Full food
rows remain local; they are not all pasted into the AI report. The report is
reviewed/shared manually. Missing days are explicitly not treated as zero.
Switching account does not combine histories. Clear session also clears this
history. The old single-snapshot prototype is not silently attributed to an
account or migrated. This is a website experiment, not an official MFP API or
background synchronization; verify the first batch on the phone.

## 0.20.0: native background download experiment

Supersedes the foreground-only 30-day approach above. The bottom **MFP** tab
shows saved summaries and sync status. The account screen remembers the username
(and migrates the last imported account) and opens its diary using the existing
WebView session. Login is only needed when that website session expires.

The confirmed batch now uses a unique WorkManager network-constrained task,
fetching only diary HTML through OkHttp. It reads the existing WebView cookie
for the exact HTTPS `www.myfitnesspal.com` diary URL, sends it only to that host,
and applies that host's Set-Cookie response through CookieManager. Redirects
are disabled; a login/challenge response stops the import rather than bypassing
it. Cookies are not copied into preferences, work input, reports, or logs.
Only username, selected date, user agent, and clear-generation are work inputs.
No images, ads, scripts, or styles are downloaded by this batch. HTML is bounded
to 2 MB and sanitized before the existing date-checked parser reads it.

Work continues independently of the Activity and screen lock. Android may defer
it under Doze, power restrictions, or loss of network; no precise completion time
is promised. Each committed day advances a durable checkpoint for the same work
ID; retries after IO errors, 429 or 5xx resume there (up to three retries, with
exponential backoff). Other HTTP/parser/auth failures require user attention.
There is a 350 ms pause between requests, and only one batch is allowed at once.
Stop cancels that task. Clearing the session cancels it and invalidates its
storage generation so an in-flight response cannot restore deleted history.
The manual WebView one-day reader remains available if native access is refused.

Local automated tests cover native HTML sanitization, multiple meals, and date
mismatch/login rejection. Actual background access and lock-screen behavior
need a phone test with an authenticated account; no live account is available
in the build environment.

## 0.21.0: browsable diary and incremental update

The MFP tab now displays private, persisted snapshots by selected date: daily
energy and macronutrients, other available nutrient totals, expandable meals
with every stored food and its returned nutrients, and a selectable seven-day
calorie chart. Day chips show whether a snapshot exists; arrows and a date
picker also allow older saved days to be opened. The saved date span, saved-day
count, and coverage of the current rolling 30-day window are shown separately,
so gaps are not represented as a complete date interval. Missing fields use a
dash, not an invented zero or daily goal. Chart heights are relative to the
largest recorded day in that week; they do not represent goal completion.

**Update diary** inspects valid snapshots for the entered account. It downloads
all missing dates within the last 30 days and always refreshes today plus the
previous two days to pick up edits. A complete previous import therefore usually
needs three requests tomorrow, not another 30. Existing older days remain saved;
old edits outside this refresh window require a manual re-read. **Account and
synchronization → Read and save 30 days** still performs an explicit full refresh
ending on its selected date.

Each worker persists its exact date plan before fetching and checkpoints its
position after saving each day. Retries reuse that plan, so a changed cache does
not shift the checkpoint. Progress shows actual done/total requests. Snapshots
are read only from the selected account namespace; corrupt or mismatched dated
snapshots are excluded from the cache and are eligible for re-download. Existing
snapshots need no migration. The ChatGPT report continues to use daily summaries
from the same stored history.
