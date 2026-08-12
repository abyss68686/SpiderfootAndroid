"""Small compatibility layer for APIs which Android does not provide."""

import os

if os.environ.get("SPIDERFOOT_ANDROID") == "1":
    # Android has no sem_open/System V IPC implementation. SpiderFoot only uses
    # Process here to keep scans off the request thread, so the thread-backed
    # drop-in retains the intended asynchronous behaviour.
    import multiprocessing.dummy as mp

    # SpiderFoot selects the real process start method during module import.
    # The thread-backed implementation has no start method to select.
    mp.set_start_method = lambda *args, **kwargs: None
else:
    import multiprocessing as mp
