// Android host: runs the Web Player in a WebView and exposes it as a MyspotiApi, plus the
// playback service (media session, notification, Android Auto browsing).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.thake.soundnest.host.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":api-spotify"))
    // NativeTokens: the page's tokens as the native engine's TokenSource (pure Kotlin, no Android code).
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.zstd)
    implementation(libs.play.services.cronet)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
    // The real org.json in unit tests (android.jar only has stubs).
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
}
