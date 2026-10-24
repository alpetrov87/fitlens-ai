# Public runner profile import

Version 0.8.0 adds **Бегач → Обнови от профила**. Paste a profile URL from my-btr.com or itra.run, fetch public data, review the editable fields and press Save. Failed requests leave existing fields untouched. Partial imports preserve fields that were not found. Saved reports identify the data source.

ITRA reference: https://github.com/HenrijsL/itra.run-scraper (reviewed README, itra_scraper.py and env.py). The example reads the runner summary and `#runner-results-table`, with results at `/RunnerSpace/RaceResults/{id}`. FitLens uses independently written parsing, no subscription cookies, no private ranking API requests. Paid race scores may be unavailable.

BTR profile pages are an Angular application, so their initial HTML does not contain the runner data. For `/runners/{uuid}` links FitLens reads the same public JSON resources used by the site: `https://api.my-btr.com/api/runner/{uuid}` and its `/results` resource. Live responses were verified on 24 October 2026. The app imports the current rating, overall place, ranking population, and up to 30 public race results. It does not authenticate, send cookies, or call private resources. The older labelled HTML/table parser remains as a fallback for other public BTR page formats.

ITRA remains an HTML import based on its public runner page and results table. Its live format may change independently of FitLens. An empty extraction shows a clear message instead of reporting a successful sync.

Fetching is HTTPS-only, bounded to 2 MB and 3 redirects, limited to the provider domain and subdomains, with public DNS address validation. Imported data is not saved or shared until reviewed. No passwords or cookies are requested.

On-device check: try an actual public profile for each provider, inspect the index and race rows against the site, save, restart FitLens, and preview the ChatGPT report. Also try invalid URLs, a login page and offline access; saved data must survive failed imports.
