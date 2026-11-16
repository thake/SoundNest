import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// -PautomixLab: a test app with its own data, installed alongside every regular/dev build.
val automixLab = findProperty("automixLab") != null


/** The ABIs a release has an APK for, and how they are named in it. */
val RELEASE_ABIS = listOf("arm64-v8a", "armeabi-v7a")
val ABI_NAMES = mapOf("arm64-v8a" to "ARMv8", "armeabi-v7a" to "ARMv7")
val APK_NAME = "SoundNest"

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
    namespace = "io.github.thake.soundnest"
    compileSdk = 37

    val version = (findProperty("appVersionName") as String?) ?: "1.0.0"
    val beta = findProperty("appVersionName") == null || version.contains("-beta")
    defaultConfig {
        applicationId = if (automixLab) "io.github.thake.soundnest.automixlab" else if (beta) "io.github.thake.soundnest.beta" else "io.github.thake.soundnest"
        manifestPlaceholders["appLabel"] = if (automixLab) "SoundNest Mix Lab" else if (beta) "SoundNest Beta" else "SoundNest"
        // Force CLI disabled outside debug builds.
        manifestPlaceholders["cliEnabled"] = "false"
        minSdk = 29
        targetSdk = 36
        // CI release builds pass the tag's version (-PappVersionName / -PappVersionCode).
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 200
        versionName = version
        // -Pabis=arm64-v8a: one APK for one phone (the test builds). Without it, the ABI splits below.
        (findProperty("abis") as String?)?.let { ndk { abiFilters += it.split(",") } }
        // On-device tests (codecs): ./gradlew connectedDebugAndroidTest
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release key from the CI secrets (env), else keystore/keystore.properties.
    val envStore = System.getenv("SOUNDNEST_KEYSTORE")
    val hasReleaseKey = envStore != null || keystorePropertiesFile.exists()
    // A stable release (a version without "-beta", given by CI) is never signed with the debug key:
    // without the release key the build stops. Local builds (no version) and -PoverDev are not stable releases.
    val stableRelease = !beta && findProperty("overDev") == null
    if (stableRelease && !hasReleaseKey && gradle.startParameter.taskNames.any { Regex("(assemble|bundle|package|install)Release").containsMatchIn(it) })
        throw GradleException("A stable release needs the release key: set SOUNDNEST_KEYSTORE (CI) or add keystore/keystore.properties.")
    signingConfigs {
        create("release") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("SOUNDNEST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SOUNDNEST_KEY_ALIAS")
                keyPassword = System.getenv("SOUNDNEST_KEY_PASSWORD")
            } else if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file("keystore/${keystoreProperties.getProperty("storeFile")}")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release apps (own data, own sign-in).
            if (!automixLab) applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = if (automixLab) "SoundNest Mix Lab" else "SoundNest dev"
            manifestPlaceholders["cliEnabled"] = "true"
        }
        release {
            // Real key when provided (CI releases); otherwise the debug key, so a local
            // release build installs over the debug one while testing.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            if (findProperty("overDev") != null && !automixLab) {
                applicationIdSuffix = ".dev"
                manifestPlaceholders["appLabel"] = "SoundNest dev"
            }
            manifestPlaceholders["cliEnabled"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Betas are release builds too, but their log reports read Log.v/d/i.
            if (!beta) proguardFiles("proguard-strip-logs.pro")
        }
    }
    splits {
        abi {
            isEnable = findProperty("abis") == null
            reset()
            include(*RELEASE_ABIS.toTypedArray())
            isUniversalApk = true
        }
    }
    packaging { jniLibs { excludes += listOf("lib/x86/**", "lib/x86_64/**") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":host-android"))
    // The native engine: the account's audio outside the Web Player page (NativeAudio), behind its setting.
    implementation(project(":engine-android"))
    // AutoMix: song analysis and transition planning (pure Kotlin), Beat This on ONNX Runtime.
    implementation(project(":automix"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    // The queue's drag and drop (Apache-2.0): the row carried stays in the list, auto-scroll at the edges.
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    // Jam: scan the invite QR code (Google code scanner, no camera permission).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    testImplementation("junit:junit:4.13.2")
    // Virtual time for the tests of timed coroutines (graces, timeouts): no real waits.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0")
    // The real org.json in unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
    // A real XML pull parser in unit tests (the TTML lyrics parser; the tests skip themselves without it).
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    // Roborazzi asks for kotlinx-io 0.3.3; Ktor (removed) had raised it to 0.9.1, the version the unit tests ran with.
    testImplementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Native UI (ported from Spotui / Orpheon, GPL-3.0)
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta10")
    // Used directly (Glide, GlideUrl, the disk cache...): beta10 only brings it at runtime.
    implementation("com.github.bumptech.glide:glide:5.0.9")
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Frosted (blurred) bars of the Immersive look. Apache-2.0.
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation(libs.serialization.json)
    // ExoPlayer for Live Radio & Local Files.
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    // HLS (.m3u8) Live Radio streams, which most big broadcasters use.
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    // Installs the Baseline Profiles shipped by Compose & co. on sideloaded builds.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(libs.okhttp)
    implementation("androidx.media3:media3-datasource:1.8.0")

    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("collectApks") {
    mustRunAfter("assembleRelease")
    val version = (findProperty("appVersionName") as String?) ?: "1.0.0"
    val beta = findProperty("appVersionName") == null || version.contains("-beta")
    val app = when { findProperty("overDev") != null -> "$APK_NAME-dev"; beta -> "$APK_NAME-Beta"; else -> APK_NAME }
    val abis = RELEASE_ABIS.toList()
    val abiNames = ABI_NAMES.toMap()
    val from = layout.buildDirectory.dir("outputs/apk/release")
    val into = layout.buildDirectory.dir("release-apks")
    doLast {
        val src = from.get().asFile
        val dst = into.get().asFile.apply { deleteRecursively(); mkdirs() }
        fun copy(name: String, to: String) {
            val f = src.resolve(name)
            if (!f.exists()) throw GradleException("$name missing: run assembleRelease without -Pabis first")
            f.copyTo(dst.resolve(to), overwrite = true)
        }
        abis.forEach { abi ->
            copy("app-$abi-release.apk", "$app-$version-${abiNames.getValue(abi)}.apk")
        }
        copy("app-universal-release.apk", "$app-$version-universal.apk")
        dst.listFiles()?.sorted()?.forEach { println("${it.name}  ${it.length() / 1024 / 1024} MB") }
    }
}
