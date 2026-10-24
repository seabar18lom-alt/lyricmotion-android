plugins { id("com.android.application") }
android {
    namespace = "com.lyricmotion.app"
    compileSdk = 35
    defaultConfig { applicationId = "com.lyricmotion.app"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "0.5" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    defaultConfig { ndk { abiFilters += "arm64-v8a" } }
}
dependencies {
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
}
