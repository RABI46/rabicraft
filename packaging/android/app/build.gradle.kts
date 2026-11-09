// The application module. `package.sh` copies the trunk output into src/main/assets/web before a
// release build; without it the APK starts and shows an empty page, so the release task checks.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.rabicraft.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "org.rabicraft.app"
        // 26 is Android 8, where adaptive icons arrive and MediaStore has the Downloads collection;
        // eframe's glow renderer needs WebGL 2 from the WebView, which Android 8's ships.
        minSdk = 26
        // Android 16 (API 36), the level Google Play requires of new apps; side-loaded builds only
        // have to satisfy the platform they run on.
        targetSdk = 36
        // Bumped by hand for a fix that must install over the build it replaces; `versionName` comes
        // from Cargo.toml (package.sh sets it).
        versionCode = 3
        versionName = "0.17.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // The app is published as a side-loadable APK, not through a store: it is signed with a
            // key the user can check (packaging/android/README.md).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].assets.srcDirs("src/main/assets")

    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/LICENSE*")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// The APK is a WebView around apps/rabicraft-web's output: refuse a build with no page in it here
// rather than in a bug report.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val site = file("src/main/assets/web/index.html")
        if (!site.isFile) {
            throw GradleException("src/main/assets/web/index.html is missing: run packaging/android/package.sh")
        }
    }
}
