# MyFitnessPal direct diary experiment — 0.18.0

Branch `experiment/mfp-direct-diary` is based on `feature/garmin-nutrition-details` so existing nutrition testing is retained. No merge or production publishing is performed. This is a manual connectivity/parser experiment, not a working official MFP API integration or background synchronization.

## Phone test

1. Install the GitHub Actions artifact for this branch, signed with the configured repository key.
2. Open Settings → MyFitnessPal → MyFitnessPal — direct access test.
3. Tap Sign in. The actual MyFitnessPal website opens in Android WebView; enter credentials only there. The displayed HTTPS host must be MyFitnessPal. This prototype supports the website's own login, not external Google/Apple OAuth. Complete normal site prompts yourself; no challenge is bypassed.
4. Enter your MyFitnessPal **username**, not email, and a date in YYYY-MM-DD. Tap Open day. Wait until the actual diary is visible. For this prototype English site headings are preferred (common Bulgarian headings are also recognized).
5. Tap Read this day. Only a table containing `tr.meal_header` is extracted. The parser reads food rows, meal headings and the daily total row; goal/remaining rows and meal subtotals are not added to intake.
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
