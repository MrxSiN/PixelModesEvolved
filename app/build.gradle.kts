plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = "0.0.4"

/*
 * Vector hot reloads system_server only when the installed versionCode differs from the loaded
 * one, so every build needs its own. CI passes VERSION_CODE, derived from the commit it is
 * building, which makes two builds of the same tag produce the same APK. A local build has no
 * commit to speak of and falls back to the wall clock: seconds since 2026-01-01 UTC.
 */
val versionEpochMillis = 1_767_225_600_000L
val buildVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull()?.takeIf { it > 0 }
    ?: ((System.currentTimeMillis() - versionEpochMillis) / 1_000L).toInt()

// Release signing key, given by CI (see .github/workflows/android.yml).
val envKeystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")
val envKeystoreAlias: String? = System.getenv("ANDROID_KEYSTORE_ALIAS")
val envKeystorePassword: String? = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val envKeyPassword: String? = System.getenv("ANDROID_KEY_PASSWORD")

android {
    namespace = "my.github.MrxSiN.modeevolved"
    compileSdk = 37

    val releaseSigningConfig = if (
        !envKeystorePath.isNullOrBlank() &&
        !envKeystoreAlias.isNullOrBlank() &&
        !envKeystorePassword.isNullOrBlank() &&
        !envKeyPassword.isNullOrBlank() &&
        file(envKeystorePath).isFile
    ) {
        signingConfigs.create("release") {
            storeFile = file(envKeystorePath)
            storePassword = envKeystorePassword
            keyAlias = envKeystoreAlias
            keyPassword = envKeyPassword
        }
    } else {
        null
    }

    defaultConfig {
        applicationId = "my.github.MrxSiN.modeevolved"
        // Modes, as the automatic zen rules this module drives, are the Android 17 (API 37) ones.
        minSdk = 37
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = appVersion
        // The map renderer is native code; phones running Android 17 are arm64.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The release key when CI provides it. Local builds use the debug key, so a local release
            // build replaces a debug one in place.
            signingConfig = releaseSigningConfig ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            optIn.addAll(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            )
        }
    }

    packaging {
        resources {
            // The Xposed framework discovers modern modules through these files.
            merges += "META-INF/xposed/*"
        }
        jniLibs {
            // The dex search runs inside Settings and loads its own native library by path,
            // which needs the library unpacked on disk rather than compressed inside the APK.
            useLegacyPackaging = true
        }
    }
}

/*
 * Resource IDs stay the same from build to build. For a moment after an update, the Settings app
 * can run hook code from the previous build while reading this build's resources, and a shifted ID
 * would show the wrong string. aapt2 keeps the IDs in this file and gives new resources new IDs;
 * after each release link, the file is refreshed from the IDs aapt2 wrote, so new ones are kept too.
 *
 * The lines are sorted on the way in. aapt2 writes them in the order of its own map, which differs
 * between machines, so an unsorted copy is a different file on every host even when every ID in it
 * is identical. Sorting makes the file a function of the IDs alone, which is what lets CI check it
 * is committed and keeps a new resource to a one-line diff. aapt2 reads the file as a lookup, so
 * the order it is given in means nothing to it.
 */
val stableResourceIds: File = layout.projectDirectory.file("resource-ids.txt").asFile

tasks.matching { it.name == "processReleaseResources" }.configureEach {
    val emitted = layout.buildDirectory.file("intermediates/stable_resource_ids_file/release/processReleaseResources/stableIds.txt")
    doLast {
        val ids = emitted.get().asFile.readLines().filter { it.isNotBlank() }.sorted()
        stableResourceIds.writeText(ids.joinToString(separator = "\n", postfix = "\n"))
    }
}

androidComponents {
    onVariants { variant ->
        if (stableResourceIds.exists()) {
            variant.androidResources.aaptAdditionalParameters.addAll("--stable-ids", stableResourceIds.path)
        }
        variant.outputs.forEach { output ->
            output.outputFileName.set("PixelModesEvolved-v$appVersion.apk")
        }
    }
}

dependencies {
    // Hook side: provided by the framework at runtime, never packaged.
    compileOnly(libs.libxposed.api)

    // Editor side: the trigger editor the Settings Mode page opens.
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.maplibre)

    // Hook side: finds a Settings class again after a quarterly release has moved it.
    implementation(libs.dexkit)

    testImplementation(libs.junit)
    // android.jar only ships stubs of org.json, so unit tests need the real one.
    testImplementation(libs.json)
}
