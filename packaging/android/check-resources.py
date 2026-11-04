#!/usr/bin/env python3
"""Fail when the manifest or the resources name something the APK does not ship.

AAPT2 only links a release build, which needs the Android SDK; a dangling @drawable is the
kind of mistake that would otherwise surface on the CI runner minutes later. This resolves the
references the same way, from the file names alone.

    packaging/android/check-resources.py
"""
import re
import sys
from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main"
RES = MAIN / "res"


def main():
    defined = set()
    for path in RES.rglob("*"):
        if path.is_file() and path.suffix in (".xml", ".png", ".webp"):
            defined.add((path.relative_to(RES).parts[0].split("-")[0], path.stem))
    for values in (RES / "values").glob("*.xml"):
        for kind, name in re.findall(r'<(color|string|style)\s+name="([^"]+)"', values.read_text(encoding="utf-8")):
            defined.add((kind, name))
    missing = []
    sources = [MAIN / "AndroidManifest.xml", *RES.rglob("*.xml")]
    for source in sources:
        for kind, name in re.findall(r"@(mipmap|drawable|color|string)/([\w.]+)", source.read_text(encoding="utf-8")):
            if (kind, name) not in defined:
                missing.append(f"{source.relative_to(MAIN)}: @{kind}/{name} does not exist")
    for problem in missing:
        print(f"check-resources: {problem}", file=sys.stderr)
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
