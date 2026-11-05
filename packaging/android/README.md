# RabiCraft on Android

A `.apk` you install directly, with no Play Store, no account and no signing service in between.
It is the browser build (`apps/rabicraft-web`) in a WebView, wrapped by the code in this folder.

## Install

1. Copy `rabicraft-<version>-android.apk` to the phone.
2. Open it. Android asks once whether to allow installs from that app (Chrome, Files, …):
   **Allow**, then **Install**.
3. RabiCraft appears in the launcher and in **Open with** for PDFs.

Side-loaded apps update by installing the newer APK over the old one; the signing certificate is
the same for every build (see [Signing](#signing)), so this keeps the app's data.

`minSdk` is 26 (Android 8) and `targetSdk` 36 (Android 16).

## What works, and what to expect

- **Everything the browser build does**: view, edit text, forms, comments, organize, redact, OCR,
  sign, export. It is the same wasm binary.
- **Saving** writes to `Downloads/RabiCraft/<name>.pdf` through MediaStore. Save As creates a new
  file; Save overwrites the one the last save wrote, as on the desktop. The file shows up in Files,
  in the Downloads app and in any picker.
- **Opening** works from the launcher, from another app's **Open with** and from **Share**. A
  document RabiCraft was started with is handed to the page as soon as it is ready.
- **Preferences, the workspace and the session** live in the WebView's `localStorage`, which is why
  the app has its own origin (below). **Back** with unsaved work asks before leaving.
- **Performance**: rendering runs on the phone's GPU through WebGL 2. Large documents are slower
  than the desktop build, and OCR is slower still.
- **Not available**: printing (the browser build does not print), and system fonts — the browser
  build embeds Inter, JetBrains Mono and Dancing Script, so documents using other typefaces show
  substitute faces exactly as the web version does.

## How it is put together

| Piece | Where | Why |
|---|---|---|
| Browser build | `apps/rabicraft-web` → `app/src/main/assets/web` | One implementation: the APK runs what the web release runs. |
| Its own origin | `MainActivity.Site` serves `https://rabicraft.localhost/` from those assets | `localStorage` is refused to `file://` documents, and any shared origin would let another site reach the app. `*.localhost` resolves to loopback, is a secure context, and needs no network permission. |
| Host bridge | `HostPage.kt` + `MainActivity.Bridge` | A WebView has no download of its own, so the page's `<a download>` save goes nowhere. Framing it with `?host=parent` makes the page post saves and receive documents instead — the protocol `apps/rabicraft-web/src/main.rs`'s `mod host` already speaks for the Nextcloud app. |
| Files | MediaStore (`Downloads/RabiCraft`) | No permission, no `MANAGE_EXTERNAL_STORAGE`, visible to the user. |
| Icon | `res/drawable/ic_launcher_foreground.xml` | Generated from `assets/app-icon/rabicraft.svg` by `icons.py`: the master artwork, as a VectorDrawable, so no raster is committed or needs regenerating offline. |

There is **no handwritten JavaScript in the build tree**: the host page is a Kotlin string
(`HostPage.kt`), and its behaviour is pinned by `app/src/test/…/HostPageTest.kt`.

## Build it

```sh
packaging/android/package.sh          # dist/release/rabicraft-<version>-android.apk
```

Needs a JDK 17+, the Android SDK (`platforms;android-36`, `build-tools;36.0.0`) in `ANDROID_HOME`,
Gradle 8.14, `trunk` and the `wasm32-unknown-unknown` target. `trunk` downloads the wasm-bindgen
CLI matching the `wasm-bindgen` in `Cargo.lock` by itself. `--skip-web` reuses the last
`trunk build` in `dist/web`.

CI does the same in `.github/workflows/android.yml`, and attaches the APK as an artifact on `main`,
on a manual run and on a release.

## Signing

The APK is **signed with the standard Android debug key**, which is what makes it installable
without a store account or a certificate. Its SHA-256 is the one every Android build tool shows for
that key (`apksigner verify --print-certs`). Two consequences:

- Anyone can rebuild the same APK and install it over this one. That is the point of a side-loadable
  build, and it is why nothing here claims the app is from a verified publisher.
- A build signed with a different key cannot update this one: uninstall first, which drops the app's
  saved preferences and workspace.

To sign it with your own key, set `signingConfig` in `app/build.gradle.kts` to a keystore you keep,
or run `apksigner sign --ks <keystore> --out <apk> <apk>` on the built file.

## Checks

`cargo xtask ci` runs the workspace and does not build the APK (no Android SDK on that runner).
`.github/workflows/android.yml` does: it builds the APK, runs the Kotlin tests of the host page, and
verifies that `assets/web/index.html` is inside the archive and that the signature verifies.
