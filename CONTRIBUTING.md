# Contributing

Issues and pull requests are welcome for Android compatibility, stability,
documentation and upstream SpiderFoot integration.

Before submitting a change:

1. Keep the local server restricted to loopback.
2. Do not add analytics, trackers or hard-coded credentials.
3. Preserve all upstream copyright and license notices.
4. Update `ANDROID_COMPATIBILITY.md` when module support changes.
5. Run `python3.10 tools/smoke_test.py` and the Android CI build.

Upstream SpiderFoot problems which are not Android-specific should be reported
to the original SpiderFoot project.
