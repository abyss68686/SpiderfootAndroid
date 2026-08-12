"""Lifecycle bridge between Android and the bundled SpiderFoot web server."""

from __future__ import annotations

import os
import re
import sys
import threading
import traceback


_lock = threading.RLock()
_status = "stopped"
_error = ""


def _set_status(status: str, error: str = "") -> None:
    global _status, _error
    with _lock:
        _status = status
        _error = error


def get_status() -> str:
    with _lock:
        return _status


def get_error() -> str:
    with _lock:
        return _error


def start_server(
    files_dir: str,
    host: str = "127.0.0.1",
    port: int = 1488,
    web_root: str = "/",
) -> None:
    """Start SpiderFoot and block until CherryPy is stopped."""
    if host not in {"127.0.0.1", "::1"}:
        raise ValueError("The Android server may only bind to a loopback address")

    port = int(port)
    if not 1024 <= port <= 65535:
        raise ValueError("Port must be between 1024 and 65535")

    if web_root != "/" and not re.fullmatch(r"/[A-Za-z0-9_-]{8,96}", web_root):
        raise ValueError("Invalid private web root")

    with _lock:
        if _status in {"starting", "running", "stopping"}:
            return
        _set_status("starting")

    files_dir = os.path.abspath(files_dir)
    data_dir = os.path.join(files_dir, "spiderfoot-data")
    cache_dir = os.path.join(data_dir, "cache")
    logs_dir = os.path.join(data_dir, "logs")
    for path in (data_dir, cache_dir, logs_dir):
        os.makedirs(path, exist_ok=True)

    os.environ["SPIDERFOOT_ANDROID"] = "1"
    os.environ["SPIDERFOOT_DATA"] = data_dir
    os.environ["SPIDERFOOT_CACHE"] = cache_dir
    os.environ["SPIDERFOOT_LOGS"] = logs_dir
    os.environ["SPIDERFOOT_WEB_ROOT"] = web_root

    import sfapp

    app_root = os.path.dirname(os.path.abspath(sfapp.__file__))
    if app_root not in sys.path:
        sys.path.insert(0, app_root)

    previous_argv = list(sys.argv)
    previous_cwd = os.getcwd()

    try:
        os.chdir(app_root)

        import cherrypy

        cherrypy.engine.subscribe("start", lambda: _set_status("running"))

        import sf

        sys.argv = ["sf.py", "-l", f"{host}:{port}"]
        sf.main()
    except SystemExit as exc:
        if exc.code in (None, 0):
            _set_status("stopped")
            return
        message = f"SpiderFoot wurde mit Status {exc.code} beendet."
        _set_status("error", message)
        raise RuntimeError(message) from exc
    except BaseException as exc:
        message = f"{type(exc).__name__}: {exc}\n{traceback.format_exc()}"
        _set_status("error", message)
        raise
    finally:
        sys.argv = previous_argv
        os.chdir(previous_cwd)
        if get_status() not in {"error", "stopped"}:
            _set_status("stopped")


def stop_server() -> None:
    """Request a clean CherryPy shutdown from the Android service."""
    with _lock:
        if _status not in {"starting", "running"}:
            return
        _set_status("stopping")

    try:
        import cherrypy

        cherrypy.engine.exit()
    except BaseException as exc:
        _set_status("error", f"Fehler beim Beenden: {type(exc).__name__}: {exc}")
        raise
