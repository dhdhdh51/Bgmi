# BGMI Scope Sensitivity Recommender

A native Android app that reads your phone's real hardware specs and recommends
BGMI camera, ADS and gyroscope sensitivity values scope-by-scope.

**Fully standalone.** No server, no account, no internet permission — the app
declares zero permissions. Everything is calculated on the device from a phone
database bundled inside the APK.

```
├── app/
│   ├── src/main/assets/phones.json   51-phone spec database (bundled)
│   └── src/main/java/…/data/         formula, database, models, history
└── .github/workflows/                signed-APK CI
```

## How it works

1. **Detect** — `DeviceSpecsHelper` reads the screen diagonal, resolution,
   density, maximum refresh rate, Android API level and gyroscope presence using
   public Android APIs only.
2. **Look up the missing piece** — touch sampling rate is not exposed by *any*
   public Android API, so it comes from the bundled `assets/phones.json`, matched
   on model.
3. **Calculate** — `SensitivityCalculator` scales the baseline values by screen
   size, refresh rate and touch sampling.
4. **Be honest about gaps** — if your phone is not in the bundled list, or a spec
   could not be read, the app uses a documented default, marks the result as an
   estimate, and lists exactly what it assumed.

## Screens

| Screen | What it does |
| --- | --- |
| Home | One "Detect my device" button; shows the hardware it read, then opens the results |
| Choose a phone | Searchable list of the bundled phones, for when detection is incomplete or you want to compare another phone |
| Recommended sensitivity | Values grouped by camera / scopes / ADS / gyroscope, with per-row **Copy** and **Copy all** |
| Saved devices | Previously detected phones with timestamps; tap to reopen, remove one, or clear all |

The gyroscope section is hidden automatically on phones without a gyroscope.
History lives in `SharedPreferences` (newest 50 entries) and is saved
automatically after each recommendation.

## Install it

Grab the APK from CI or build it yourself:

```bash
./gradlew assembleDebug      # debug APK, applicationId suffix .debug
./gradlew assembleRelease    # minified release APK (R8)
```

Requires JDK 17+ and the Android SDK. There is nothing to configure first — no
URLs, no keys, no config files.

Every push to `main` uploads an `app-release-signed` artifact. Pushing a `v*` tag
also publishes a GitHub release with the APK attached.

## Signing keystore secrets

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

The workflow runs `apksigner verify --print-certs` so a mis-signed APK fails the
run instead of shipping. **Without these secrets the build still succeeds, but
the APK is signed with the debug key and is not distributable.**

Keep `release.keystore` and `keystore_base64.txt` out of the repository (both are
git-ignored). **To rotate the key:** generate a new keystore, update all four
secrets, then bump `versionCode`. An APK signed with a new key cannot upgrade an
install signed with the old one — users must reinstall.

## Adding or correcting phones

Everything lives in [`app/src/main/assets/phones.json`](app/src/main/assets/phones.json).
Add an object and rebuild:

```json
{
  "manufacturer": "samsung",
  "model": "Galaxy A55",
  "screen_size_inches": 6.6,
  "density_dpi": 450,
  "refresh_rate_hz": 120,
  "touch_sampling_rate_hz": 240,
  "build_model_codes": ["SM-A556E", "SM-A556B"]
}
```

`build_model_codes` is what makes auto-detection reliable. `Build.MODEL` is not
standardised: some phones report a marketing name (`Redmi Note 12`), others an
internal code (`SM-A546E`, `CPH2451`). Get the real value with:

```bash
adb shell getprop ro.product.model
```

Matching order: exact model name → `build_model_codes` → normalised comparison
(ignoring case, spaces and punctuation) → give up and flag an estimate.

**About the bundled data:** screen sizes and refresh rates are published
manufacturer figures. **Touch sampling rates are conservative estimates** —
vendors quote peak or "instantaneous" rates that panels do not sustain. Correct
them freely; that is the single most useful contribution to this app.

CI validates the file on every push (required keys, plausible ranges, duplicate
models), so a typo fails the build instead of crashing on someone's phone.

## Tuning the formula

`app/src/main/java/com/bgmi/sensitivity/data/SensitivityCalculator.kt` is the
only file to touch. Baselines are the values validated on a 6.5" reference
device, scaled by three factors:

| Input | Effect |
| --- | --- |
| Screen size | `size / 6.5`, clamped to 0.85–1.20 |
| Refresh rate | ×1.00 below 90 Hz, ×1.05 at 90–143 Hz, ×1.08 at 144 Hz+ |
| Touch sampling | ×1.00 below 360 Hz, ×0.99 at 360–479 Hz, ×0.97 at 480 Hz+ |

Results are rounded and clamped to BGMI's valid 1–300 range. A 6.5" 60 Hz phone
reproduces the baselines exactly, which makes accidental changes easy to spot.

Because the app is offline by design, **retuning means shipping a new APK.**
That is the deliberate trade-off for having no server to run: fewer moving parts
and nothing to pay for, at the cost of updates going through a release.

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

**AGP 9 has built-in Kotlin support**, so no Kotlin Gradle plugin is applied —
adding `org.jetbrains.kotlin.android` would conflict with it. Kotlin compiler
options live in the top-level `kotlin { compilerOptions { … } }` block, not the
removed `android { kotlinOptions { … } }`.

Two CI details worth knowing: the SDK now uses **minor-versioned platform
packages** (API level 37 is `platforms;android-37.0`, not `android-37`), and
`gradle/actions/setup-gradle` is deliberately avoided because its caching
component became proprietary and separately licensed in v6 —
`actions/setup-java`'s Gradle cache covers the same ground under MIT.

## Notes and limitations

- Sensitivity is personal. These values are a *starting point* — the results
  screen says so, and Training mode is still the final judge.
- Recommendations for phones missing from the bundled list are honest about it,
  with a bullet list of every assumption made.
- Nothing leaves your phone. The app has no network code and no permissions.
