#!/usr/bin/env python3
"""Fail when the APK's host page frames a page the APK does not ship.

`HostPage.kt` frames the browser build, and a frame pointed at a missing file is not an error
anywhere: the WebView answers 404, the iframe stays blank, and the phone shows an empty window.
That shipped once (the frame asked for `./app/index.html`, `package.sh` copied the site to the
root of `assets/web`). This checks the two together, before the APK is built.

    packaging/android/check-page.py [--site DIR]

`--site` checks the trunk output before it is copied in; without it, the assets the release build
will ship.
"""
import argparse
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "app/src/main/kotlin/org/rabicraft/app/HostPage.kt"
ASSETS = HERE / "app/src/main/assets/web"

# `const val FRAME = "./index.html?host=parent"` — read from the Kotlin, not repeated here.
FRAME = re.compile(r'const val FRAME = "([^"]+)"')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, help="the built site (dist/web) instead of the assets")
    site = parser.parse_args().site or ASSETS

    match = FRAME.search(SOURCE.read_text(encoding="utf-8"))
    if not match:
        print(f"check-page: no FRAME in {SOURCE.name}; the framed page cannot be checked", file=sys.stderr)
        return 1
    frame = match.group(1)
    # A query string picks how the page behaves, not which file it is.
    path = frame.split("?", 1)[0].removeprefix("./")
    if not path or path.endswith("/"):
        path += "index.html"

    target = site / path
    if not target.is_file():
        print(f"check-page: the host page frames {frame!r}, but {target} does not exist", file=sys.stderr)
        return 1
    if "host=parent" not in frame:
        print(f"check-page: {frame!r} does not ask for ?host=parent: saves would download instead of reaching the app", file=sys.stderr)
        return 1
    # The page needs a canvas for the renderer, and trunk writes the wasm next to it.
    index = target.read_text(encoding="utf-8")
    if 'id="rabicraft"' not in index:
        print(f"check-page: {target} has no #rabicraft canvas", file=sys.stderr)
        return 1
    print(f"check-page: {frame} -> {target.relative_to(HERE if site == ASSETS else site)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
