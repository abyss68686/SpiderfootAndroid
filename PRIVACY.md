# Privacy

AbyssSpiderfoot adds no analytics, advertising SDK, account system or telemetry.
The local web server listens only on `127.0.0.1:1488` and uses a random private
URL root generated for each installation.

Scan history, settings and API keys are stored in Android's private application
directory. Android removes this directory when the app is uninstalled.

SpiderFoot is a network intelligence tool. Depending on the modules selected,
scan targets, discovered values and API credentials are sent directly to the
third-party services configured in those modules. Those services have their own
privacy policies. AbyssSpiderfoot does not proxy or receive that traffic.

Exports are written to the user's Android Downloads directory only when the user
requests them.
