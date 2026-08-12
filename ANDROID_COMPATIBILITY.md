# Android-Kompatibilität

## Enthalten

Die originale SpiderFoot-4.0.0-Weboberfläche, SQLite-Speicherung, Korrelationen, Einstellungen, API-Schlüssel, Importe, Exporte und alle reinen Python-/Netzwerkmodule sind enthalten. Beim lokalen End-to-End-Test wurden Startseite, neue Scans, Einstellungen, statische Dateien und ein vollständig abgeschlossener Scan über den Thread-Ersatz geprüft.

## Bewusst entfernte externe Tool-Module

Diese 13 Module starten Programme, die nicht Teil von SpiderFoot oder der eingebetteten Python-Laufzeit sind:

- `sfp_tool_cmseek`
- `sfp_tool_dnstwist`
- `sfp_tool_nbtscan`
- `sfp_tool_nmap`
- `sfp_tool_nuclei`
- `sfp_tool_onesixtyone`
- `sfp_tool_retirejs`
- `sfp_tool_snallygaster`
- `sfp_tool_testsslsh`
- `sfp_tool_trufflehog`
- `sfp_tool_wafw00f`
- `sfp_tool_wappalyzer`
- `sfp_tool_whatweb`

Sie werden nicht bloß deaktiviert, sondern aus dem Android-Modulverzeichnis ausgelassen. Dadurch erscheinen sie weder im Scan-Dialog noch in den Einstellungen und können nicht versehentlich ausgewählt werden.

## Weitere Grenzen

- Es wird kein Selenium/WebDriver eingebettet. Die vorliegende SpiderFoot-4.0.0-Quelle enthält selbst kein Selenium-Modul.
- Ein Android-Hersteller kann sehr lange Vordergrunddienste dennoch über seine Akkuverwaltung beenden. Für mehrstündige Scans sollte die Akkuoptimierung für die App bei Bedarf manuell deaktiviert werden.
- Der Server ist nicht als Dienst für andere Geräte gedacht. Er bleibt auf Loopback und verwendet zusätzlich einen zufälligen privaten URL-Pfad.
- Die APK enthält nur `arm64-v8a`; x86-Emulatoren und reine 32-Bit-Geräte werden nicht unterstützt.
