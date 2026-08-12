# Security policy

## Supported version

Only the newest published AbyssSpiderfoot build is supported during the beta.

## Reporting a vulnerability

Open a GitHub issue for ordinary bugs. For a vulnerability, do not publish API
keys, personal scan data, private targets or working exploit details in a public
issue. First provide a minimal description and request a private contact channel.

## Security design

- CherryPy binds exclusively to `127.0.0.1`.
- Every installation receives an unguessable private web root.
- The WebView accepts the local route only and opens external links separately.
- TLS errors are never bypassed.
- WebView remote debugging is disabled.
- Android backup is disabled for the application.

The application is an OSINT client, not a network service intended for exposure
to other devices.
