# BGMI Scope Sensitivity Recommender

Reads a phone's real hardware specs and recommends BGMI camera, ADS and
gyroscope sensitivity values scope-by-scope. A native Kotlin app collects the
specs, a PHP/MySQL API does the maths, and GitHub Actions ships a signed APK.

The app contains **no calculation logic at all** — every formula lives in
`backend/lib/Sensitivity.php`, so recommendations can be retuned server-side
without publishing an app update.

```
├── app/                      Android app (Kotlin, Views + Material 3)
├── backend/                  PHP 8 JSON API (no framework, no Composer)
├── database/                 MySQL schema + 51-phone seed
└── .github/workflows/        Signed-APK CI
```

## How it works

1. **Detect** — `DeviceSpecsHelper` reads screen diagonal, resolution, density,
   max refresh rate, Android API level and gyroscope presence from public APIs.
2. **Ask** — the app POSTs those specs to `/calculate-sensitivity`.
3. **Fill the gap** — touch sampling rate is not exposed by *any* public Android
   API, so the backend supplies it from the `phones` table, matching on model.
   Nothing found? A documented default is used and the response is flagged
   `is_estimate`, which the UI surfaces as "values were estimated".
4. **Render** — the app displays whatever JSON comes back and saves it to a
   local history.

## Screens

| Screen | What it does |
| --- | --- |
| Home | One "Detect my device" button; shows the hardware it read, then opens the results |
| Choose a phone | Searchable list from the backend, for when detection is incomplete or you want to compare another phone |
| Recommended sensitivity | Values grouped by camera / scopes / ADS / gyroscope, with per-row **Copy** and **Copy all** |
| Saved devices | Previously detected phones with timestamps; tap to reopen, remove one, or clear all |
| Feedback | 1–5 rating per setting, stored server-side for future tuning |

The gyroscope section is hidden automatically on phones without a gyroscope.

---

## 1. Configure the backend URL in the app

`BuildConfig.API_BASE_URL` is baked in at build time and must end with `/`.
Resolution order (first match wins):

1. `./gradlew assembleRelease -Pbgmi.apiBaseUrl=https://api.example.com/api/`
2. `bgmi.apiBaseUrl=https://api.example.com/api/` in `local.properties` (git-ignored — best for local dev)
3. `bgmi.apiBaseUrl` in `gradle.properties` (the checked-in default placeholder)

In CI, set a repository **variable** named `API_BASE_URL`
(*Settings → Secrets and variables → Actions → Variables*) and the workflow
passes it through automatically.

> The app sets `usesCleartextTraffic="false"`, so the URL must be **HTTPS**.
> A plain-HTTP endpoint will fail to connect.

## 2. Deploy the API (cPanel / LiteSpeed)

```bash
# 1. Upload the contents of backend/ to public_html/api/
# 2. Create the database and user in cPanel, then import:
mysql -u cpaneluser_bgmi -p cpaneluser_bgmi < database/schema.sql
mysql -u cpaneluser_bgmi -p cpaneluser_bgmi < database/seed_phones.sql
# 3. Configure credentials:
cp config.sample.php config.php   # then edit config.php
```

Check it: `https://your-domain.com/api/` returns a JSON health payload listing
the endpoints.

`config.php` holds credentials and is git-ignored — never commit it. Both it and
`lib/` are blocked from direct web access by the shipped `.htaccess` files. If
your host has no `mod_rewrite`, the routes also work unrewritten as
`/api/index.php?route=phones`.

### Endpoints

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/phones?search=poco` | Search phones (manual-select autocomplete) |
| `GET` | `/phone-specs?model=Poco%20X6%20Pro` | Specs for one model |
| `POST` | `/calculate-sensitivity` | Recommendation for the posted specs |
| `POST` | `/feedback` | Store a 1–5 rating |

```bash
curl -sX POST https://your-domain.com/api/calculate-sensitivity \
  -H 'Content-Type: application/json' \
  -d '{"model":"Redmi Note 12","screen_size_inches":6.67,"density_dpi":440,
       "refresh_rate_hz":120,"android_sdk_int":34}'
```

```json
{
  "model": "Redmi Note 12",
  "phone_id": 4,
  "matched_in_db": true,
  "is_estimate": false,
  "notes": [],
  "inputs_used": {
    "screen_size_inches": 6.67, "density_dpi": 440, "refresh_rate_hz": 120,
    "touch_sampling_rate_hz": 240, "touch_sampling_source": "database",
    "android_sdk_int": 34,
    "factors": {"screen": 1.0262, "refresh": 1.05, "touch_sampling": 1.0, "combined": 1.0775}
  },
  "sensitivities": {
    "camera": {"free_look": 48, "tpp_no_scope": 97, "fpp_no_scope": 102},
    "red_dot_holo_2x": {"tpp": 67, "fpp": 70},
    "scope_3x": 30, "scope_4x": 24, "scope_6x": 18, "scope_8x": 12,
    "ads_sensitivity": 59,
    "gyroscope": {"3x": 129, "4x": 97, "6x": 59, "8x": 38}
  }
}
```

Only `model` is required. Every other field is optional: whatever the app could
not measure is taken from the database, and whatever the database does not know
either falls back to `config.php → defaults` — which sets `is_estimate` and adds
a human-readable line to `notes` explaining exactly what was assumed.
`inputs_used` echoes back the numbers and factors actually used, which makes
tuning and debugging much easier.

Errors are JSON with matching HTTP codes: `400` malformed body, `404` unknown
route/phone, `405` wrong method, `422` validation, `500`/`503` server or database.

## 3. Signing keystore secrets

One-time setup:

```bash
keytool -genkey -v -keystore release.keystore -alias appkey \
        -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore > keystore_base64.txt
```

Add four repository secrets under *Settings → Secrets and variables → Actions*:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | contents of `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `appkey` |
| `KEY_PASSWORD` | key password |

Every push to `main` then produces an `app-release-signed` artifact; the workflow
runs `apksigner verify --print-certs` so a mis-signed APK fails loudly. Pushing a
`v*` tag additionally publishes a GitHub release with the APK attached.

Keep `release.keystore` and `keystore_base64.txt` off the repository (both are
git-ignored). **To rotate the key:** generate a new keystore, update all four
secrets, then bump `versionCode`. Note that an APK signed with a new key cannot
upgrade an install signed with the old one — users must reinstall (or you enrol
in Play App Signing before the first release).

Without the secrets the build still succeeds, but `assembleRelease` falls back to
the debug signing key and the APK is **not** distributable.

## 4. Adding or correcting phones

Auto-detection matches `Build.MODEL` against the `phones` table by exact model,
then by the optional `build_model_codes` list, then by a normalised comparison
(case- and punctuation-insensitive). Adding the raw code is what makes detection
reliable, because `Build.MODEL` is often an internal name like `SM-A546E`:

```sql
-- Get the code from the device:  adb shell getprop ro.product.model
INSERT INTO phones (manufacturer, model, screen_size_inches, density_dpi,
                    refresh_rate_hz, touch_sampling_rate_hz, build_model_codes)
VALUES ('samsung', 'Galaxy A55', 6.60, 450, 120, 240, 'SM-A556E,SM-A556B');

UPDATE phones SET touch_sampling_rate_hz = 480 WHERE model = 'Poco X6 Pro';
```

`database/seed_phones.sql` is idempotent — re-importing updates existing rows
instead of duplicating them. Screen sizes and refresh rates there are published
manufacturer figures; **touch sampling rates are conservative estimates**, since
vendors quote peak rather than sustained values. The `/feedback` endpoint exists
to correct them over time:

```sql
SELECT p.model, COUNT(*) AS ratings, ROUND(AVG(f.rating), 2) AS avg_rating
FROM feedback f LEFT JOIN phones p ON p.id = f.phone_id
GROUP BY p.model ORDER BY avg_rating;
```

## 5. Tuning the formula

`backend/lib/Sensitivity.php` is the only file to touch. Baselines are the values
validated on a 6.5" reference device, scaled by three factors:

| Input | Effect |
| --- | --- |
| Screen size | `size / 6.5`, clamped to 0.85–1.20 |
| Refresh rate | ×1.00 below 90 Hz, ×1.05 at 90–143 Hz, ×1.08 at 144 Hz+ |
| Touch sampling | ×1.00 below 360 Hz, ×0.99 at 360–479 Hz, ×0.97 at 480 Hz+ |

Results are rounded and clamped to BGMI's valid 1–300 range. A 6.5" 60 Hz phone
reproduces the baselines exactly, which makes regressions easy to spot.

## Tooling versions

Verified against the current releases in September 2026 rather than assumed —
worth re-checking before you bump anything:

| Component | Version | Note |
| --- | --- | --- |
| Android Gradle plugin | 9.3.0 | Requires Gradle 9.5+, JDK 17+, build-tools 36.0.0 |
| Kotlin | supplied by AGP | Built-in Kotlin — no Kotlin version is declared anywhere |
| Gradle | 9.5.0 | AGP 9.3's default; wrapper JAR checksum verified against Gradle's published list |
| `compileSdk` / `targetSdk` | 37 / 36 | Android 17 SDK; API 36 meets the current Play requirement |
| `minSdk` | 26 | Covers adaptive icons and effectively the whole active install base |
| JDK | 17 | AGP 9.3 minimum *and* default |
| PHP | 8.0+ | Needs `pdo_mysql`; `mbstring` optional |
| MySQL / MariaDB | 5.7+ / 10.2+ | `CHECK` constraints need MySQL 8.0.16+ or MariaDB 10.2.1+ |

**AGP 9 has built-in Kotlin support**, so no Kotlin Gradle plugin is applied —
adding `org.jetbrains.kotlin.android` would conflict with it. Kotlin compiler
options live in the top-level `kotlin { compilerOptions { … } }` block, not the
removed `android { kotlinOptions { … } }`.

CI deliberately uses `actions/setup-java`'s Gradle cache instead of
`gradle/actions/setup-gradle`, whose caching component is proprietary and
separately licensed from v6 onwards.

## Building locally

```bash
./gradlew assembleDebug      # debug APK, applicationId suffix .debug
./gradlew assembleRelease    # minified release APK (R8)
```

Requires JDK 17+ and the Android SDK. Set `bgmi.apiBaseUrl` first (see step 1),
otherwise the app points at the `https://example.com/api/` placeholder and every
request fails.

## Notes and limitations

- Sensitivity is personal. These values are a *starting point* — the results
  screen says so, and Training mode is still the final judge.
- Recommendations for phones missing from the database are honest about it
  (`is_estimate`, plus an explanation in `notes`).
- History is stored locally in `SharedPreferences` (newest 50 entries) and is
  saved automatically after each successful recommendation; nothing personal is
  sent to the server beyond the specs shown on the Home screen.
- The API is unauthenticated by design (it serves public, non-personal data). If
  you expose it widely, put rate limiting in front of it.
