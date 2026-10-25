# Public runner profile import

Version 0.8.0 adds **Бегач → Обнови от профила**. Paste a profile URL from my-btr.com or itra.run, fetch public data, review the editable fields and press Save. Failed requests leave existing fields untouched. Partial imports preserve fields that were not found. Saved reports identify the data source.

ITRA reference: https://github.com/HenrijsL/itra.run-scraper (reviewed README, itra_scraper.py and env.py). The example reads the runner summary and `#runner-results-table`, with results at `/RunnerSpace/RaceResults/{id}`. FitLens uses independently written parsing, no subscription cookies, no private ranking API requests. Paid race scores may be unavailable.

BTR profile pages are an Angular application, so their initial HTML does not contain the runner data. For `/runners/{uuid}` links FitLens reads the same public JSON resources used by the site: `https://api.my-btr.com/api/runner/{uuid}` and its `/results` resource. Live responses were verified on 24 October 2026. The app imports the current rating, overall place, ranking population, and up to 30 public race results. It does not authenticate, send cookies, or call private resources. The older labelled HTML/table parser remains as a fallback for other public BTR page formats.

ITRA protects its public runner pages and old public endpoints with an AWS WAF JavaScript challenge, which returns HTTP 202 and no usable body to an Android HTTP client. FitLens first tries the direct public page, then uses `https://r.jina.ai/` as a fallback text renderer. Only the already-public ITRA profile URL is sent to Jina Reader; no Garmin data, keys, cookies, passwords, or local FitLens data are included. The parsed fields are Performance Index, finished-race count, ITRA 1–6 point counts, and public result summaries. The fallback is a third-party service with its own availability and rate limits.

An empty extraction shows a clear message instead of reporting a successful sync. Both public sites can change their response formats independently of FitLens.

Fetching is HTTPS-only, bounded to 2 MB and 3 redirects, limited to the provider domain and subdomains, with public DNS address validation. Imported data is not saved or shared until reviewed. No passwords or cookies are requested.

On-device check: try an actual public profile for each provider, inspect the index and race rows against the site, save, restart FitLens, and preview the ChatGPT report. Also try invalid URLs, a login page and offline access; saved data must survive failed imports.
