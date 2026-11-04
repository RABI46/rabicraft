// The Android package of RabiCraft: the browser build in a WebView (see
// packaging/android/README.md). Build it with packaging/android/package.sh, which puts the trunk
// output in app/src/main/assets/web first.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "rabicraft-android"
include(":app")
