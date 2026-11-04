#!/usr/bin/env bash
# Build the RabiCraft APK to side-load (no store, no account):  $DIST/rabicraft-<version>-android.apk
#
# Usage: packaging/android/package.sh [--skip-web]
#
# Needs: a JDK 17+, the Android SDK (compileSdk 36, build-tools 36.0.0) with ANDROID_HOME or
# ANDROID_SDK_ROOT set, and — unless --skip-web — trunk and the wasm32-unknown-unknown target. The
# browser build (`apps/rabicraft-web`) is what the APK runs; the Android side is the WebView and the
# host bridge around it (README.md here).
#
# The APK is signed with the debug key: it installs on any phone that allows unknown sources, and
# the certificate is what the user can check (README.md, "Signing").
set -euo pipefail
# shellcheck source=../env.sh
. "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
HERE="$ROOT/packaging/android"
APP="$HERE/app"
WEB="$APP/src/main/assets/web"

if [[ "${1:-}" != "--skip-web" ]]; then
  command -v trunk >/dev/null || { echo "error: trunk not found (cargo install trunk --locked)" >&2; exit 1; }
  (cd "$ROOT/apps/rabicraft-web" && trunk build --release)
  SITE="$ROOT/dist/web"
  [[ -f "$SITE/index.html" ]] || { echo "error: $SITE/index.html missing; rerun without --skip-web" >&2; exit 1; }
  # The host page frames ./app/index.html, so the site keeps its own folder under assets/.
  rm -rf "$WEB"
  mkdir -p "$WEB"
  cp -R "$SITE/." "$WEB/"
  copy_docs "$WEB"
fi
[[ -f "$WEB/index.html" ]] || { echo "error: $WEB/index.html missing; rerun without --skip-web" >&2; exit 1; }

# The launcher icon is generated from the committed master; refuse a stale one, and refuse a
# resource reference the APK has no file for (AAPT2 would only say so at link time).
python3 "$HERE/icons.py" --check
python3 "$HERE/check-resources.py"

# The version the APK reports comes from the one place it lives.
sed -i.bak -E "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$APP/build.gradle.kts"
rm -f "$APP/build.gradle.kts.bak"

# No wrapper is committed (its jar is a binary the release pipeline would have to trust): the
# runner's Gradle runs the wrapper's own properties, so a local build and CI resolve the same one.
cd "$HERE"
if [[ -x ./gradlew ]]; then
  ./gradlew --no-daemon :app:assembleRelease
else
  command -v gradle >/dev/null || { echo "error: gradle not found (or commit the wrapper: gradle wrapper)" >&2; exit 1; }
  gradle --no-daemon --offline=false :app:assembleRelease
fi
BUILT="$APP/build/outputs/apk/release/app-release.apk"
[[ -f "$BUILT" ]] || { echo "error: $BUILT missing" >&2; exit 1; }

OUT="$DIST/rabicraft-$VERSION-android.apk"
cp "$BUILT" "$OUT"
echo "wrote $OUT"
ls -lh "$OUT"
# What a phone will show before installing a side-loaded app.
if command -v apksigner >/dev/null; then
  apksigner verify --print-certs "$OUT" | head -5
else
  warn "apksigner not on PATH; skipped the signature check"
fi
