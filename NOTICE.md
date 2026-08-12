# Notices and attribution

## AbyssSpiderfoot Android integration

Copyright (c) 2026 Steve Linke. Licensed under the MIT License in `LICENSE`.

## SpiderFoot

AbyssSpiderfoot contains and modifies the SpiderFoot 4.0.0 master snapshot
`0f815a203afebf05c98b605dba5cf0475a0ee5fd`, created by Steve Micallef.
That snapshot is distributed under the MIT License. Its original copyright and
license text are retained at `app/src/main/python/sfapp/LICENSE`.

Upstream project: https://github.com/smicallef/spiderfoot

## Chaquopy and Python runtime

Chaquopy 17.0.0 is distributed under the MIT License. The embedded CPython
runtime and its native dependencies retain their own upstream licenses.

Chaquopy source: https://github.com/chaquo/chaquopy

## Python and web dependencies

The exact direct Python versions are listed in
`app/src/main/python/requirements-android.txt`; transitive versions are pinned
in `app/src/main/python/constraints-android.txt`. Their package metadata and
license files are retained inside the APK's Chaquopy requirement archives.

The dependency set includes permissive MIT, BSD, ISC, Apache-2.0, PSF and ZPL
software. It also contains MPL-2.0 components (`certifi` and
`publicsuffixlist`) and an LGPL-3.0 component vendored by `setuptools`
(`autocommand`). Exact corresponding sources can be obtained from the named
package versions on PyPI; no local modifications are made to those packages.

The SpiderFoot interface includes its upstream copies of jQuery, Bootstrap,
D3, AlertifyJS, Sigma.js and jQuery Tablesorter. Their upstream notices remain
applicable.

## Ispell dictionaries

SpiderFoot includes Ispell word lists under their retained license at
`app/src/main/python/sfapp/spiderfoot/dicts/ispell/LICENSE`.

Required acknowledgement:

> This product includes software developed by Geoff Kuenning and other unpaid
> contributors.

No upstream author or contributor endorses AbyssSpiderfoot.
