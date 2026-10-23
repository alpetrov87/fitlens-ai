# Public runner profile import

Version 0.8.0 adds **Бегач → Обнови от профила**. Paste a profile URL from my-btr.com or itra.run, fetch public data, review the editable fields and press Save. Failed requests leave existing fields untouched. Partial imports preserve fields that were not found. Saved reports identify the data source.

ITRA reference: https://github.com/HenrijsL/itra.run-scraper (reviewed README, itra_scraper.py and env.py). The example reads the runner summary and `#runner-results-table`, with results at `/RunnerSpace/RaceResults/{id}`. FitLens uses independently written parsing, no subscription cookies, no private ranking API requests. Paid race scores may be unavailable.

BTR uses labelled BTR index/rating text, result tables identified by race/time headers, and embedded JSON with explicit BTR index fields. These are best-effort patterns tested on representative fixtures, not a verified BTR API. Direct access to both sites is blocked by the development network proxy (CONNECT 403), so live end-to-end compatibility has not been established. JavaScript-only content and private profiles are not supported by this HTML importer. An empty extraction shows a clear message instead of reporting a successful sync.

Fetching is HTTPS-only, bounded to 2 MB and 3 redirects, limited to the provider domain, with public DNS address validation. Imported data is not saved or shared until reviewed. No passwords or cookies are requested.

On-device check: try an actual public profile for each provider, inspect the index and race rows against the site, save, restart FitLens, and preview the ChatGPT report. Also try invalid URLs, a login page and offline access; saved data must survive failed imports.
