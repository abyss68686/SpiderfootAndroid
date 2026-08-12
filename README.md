# AbyssSpiderfoot

**AbyssSpiderfoot** is an unofficial Android community port of
[SpiderFoot](https://github.com/smicallef/spiderfoot), the open-source OSINT
automation platform created by Steve Micallef.

The SpiderFoot 4.0.0 Python core and CherryPy server run entirely inside the
Android application. The original web interface is displayed in a hardened
WebView. No external desktop server is required.

> This project is not affiliated with or endorsed by the original SpiderFoot
> project. Use it only for systems and targets you are authorized to assess.

## Download

The latest tested community build is available under
[Releases](https://github.com/abyss68686/AbyssSpiderfoot/releases).

The first `0.1.0-beta.1` preview is ARM64-only and uses an Android preview
certificate. A later stable release will use a permanent project signing key.

## Features

- Local server bound only to `127.0.0.1:1488`
- Random private URL root for every installation
- Original SpiderFoot web UI, settings, API-key import/export and correlations
- Persistent private SQLite database, configuration, cache and logs
- Foreground service and partial wake lock for long-running scans
- CSV, JSON, XLSX, GEXF and configuration downloads
- External links are opened in the user's regular browser
- WebView remote debugging is disabled
- No analytics, advertising SDK or telemetry added by AbyssSpiderfoot

## Android compatibility

- Android 7.0 or newer (API 24)
- `arm64-v8a`
- Target SDK 35

SpiderFoot normally starts scans with `multiprocessing.Process`. Android does
not provide the required CPython IPC implementation, so this port uses
`multiprocessing.dummy.Process` threads behind the same interface. External
desktop tools and Selenium/WebDriver are not bundled. See
[ANDROID_COMPATIBILITY.md](ANDROID_COMPATIBILITY.md) for the exact limits.

## Build

Requirements:

- JDK 17
- Python 3.10
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.11.1

```bash
gradle :app:assembleDebug -PpythonCommand=python3.10
```

The CI workflow runs a SpiderFoot end-to-end smoke test, builds the APK, and
verifies its Android signature and ZIP alignment before publishing an artifact.

## Data and privacy

Application data is stored under Android's private app directory. Uninstalling
the app removes its database, settings and API keys, so export important data
first. Individual SpiderFoot modules contact their configured third-party data
sources. See [PRIVACY.md](PRIVACY.md).

## Credits and licenses

- SpiderFoot: Copyright 2022 Steve Micallef, MIT License
- Android integration: Copyright 2026 Steve Linke, MIT License
- Chaquopy: MIT License
- Other bundled components retain their respective license and notice files

See [NOTICE.md](NOTICE.md), the root [LICENSE](LICENSE), SpiderFoot's retained
license at `app/src/main/python/sfapp/LICENSE`, and the package license metadata
embedded in every APK.

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).
