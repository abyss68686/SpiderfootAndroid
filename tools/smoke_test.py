#!/usr/bin/env python3
"""Host-side end-to-end test for the Android-adapted SpiderFoot core."""

from __future__ import annotations

import json
import sys
import tempfile
import threading
import time
import urllib.parse
import urllib.request
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PYTHON_ROOT = PROJECT_ROOT / "app" / "src" / "main" / "python"
sys.path.insert(0, str(PYTHON_ROOT))

import android_entrypoint  # noqa: E402


HOST = "127.0.0.1"
PORT = 14880
PRIVATE_ROOT = "/android-0123456789abcdef0123456789abcdef"
BASE_URL = f"http://{HOST}:{PORT}{PRIVATE_ROOT}/"


def request(path: str = "", data: dict[str, str] | None = None):
    encoded = urllib.parse.urlencode(data).encode() if data is not None else None
    headers = {"Accept": "application/json"} if data is not None else {}
    req = urllib.request.Request(BASE_URL + path, data=encoded, headers=headers)
    return urllib.request.urlopen(req, timeout=15)


def wait_until_ready() -> None:
    last_error: BaseException | None = None
    for _ in range(120):
        try:
            with request() as response:
                if response.status == 200:
                    return
        except BaseException as exc:
            last_error = exc
        time.sleep(0.25)
    raise RuntimeError(f"Server did not become ready: {last_error}")


def run_scan() -> str:
    with request("startscan", {
        "scanname": "Android compatibility smoke test",
        "scantarget": "example.com",
        "modulelist": "module_sfp_base64",
        "typelist": "",
        "usecase": "",
    }) as response:
        result = json.loads(response.read())
    assert result[0] == "SUCCESS", result
    scan_id = result[1]

    for _ in range(120):
        with request("scanstatus?id=" + urllib.parse.quote(scan_id)) as response:
            status = json.loads(response.read())
        if status and status[5] in {"FINISHED", "ERROR-FAILED", "ABORTED"}:
            assert status[5] == "FINISHED", status
            return scan_id
        time.sleep(0.25)
    raise RuntimeError("Scan did not finish within 30 seconds")


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="spiderfoot-android-test-") as files_dir:
        server = threading.Thread(
            target=android_entrypoint.start_server,
            args=(files_dir, HOST, PORT, PRIVATE_ROOT),
            name="spiderfoot-test-server",
            daemon=True,
        )
        server.start()

        try:
            wait_until_ready()

            with request("newscan") as response:
                new_scan_page = response.read().decode("utf-8")
            with request("opts") as response:
                settings_page = response.read().decode("utf-8")

            assert "sfp_base64" in new_scan_page
            assert "sfp_tool_nmap" not in new_scan_page
            assert "Global Settings" in settings_page

            scan_id = run_scan()
            print(json.dumps({
                "server": "ok",
                "web_ui": "ok",
                "settings": "ok",
                "threaded_scan": "FINISHED",
                "scan_id": scan_id,
            }, indent=2))
        finally:
            android_entrypoint.stop_server()
            server.join(timeout=10)
            if server.is_alive():
                raise RuntimeError("Server did not stop cleanly")


if __name__ == "__main__":
    main()
