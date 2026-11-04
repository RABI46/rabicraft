// Pinned so the release APK is reproducible: packaging/android/package.sh reads the versions the
// CI job installs from these two lines.
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}
