# AbyssSpiderfoot 0.1.0 beta 1

First public community preview of the unofficial SpiderFoot Android port.

## Included

- SpiderFoot 4.0.0 core and original web interface
- Local-only CherryPy server on `127.0.0.1:1488`
- Random private URL root per installation
- Persistent settings, API keys, scan database and exports
- Android foreground service for long-running scans
- ARM64 support for Android 7.0 and newer
- Reproducibly pinned Python dependencies
- Signature, alignment and end-to-end smoke-test verification in CI

## Known limitations

- External desktop tool modules and Selenium are not included.
- Scan processes use Python threads on Android and can be slower than desktop.
- This preview uses a non-permanent Android preview certificate. A future stable
  release will use the permanent project signing key and may require reinstalling
  this beta once.

This is an unofficial community project and is not endorsed by the upstream
SpiderFoot project. Use it only on systems and targets you are authorized to assess.
