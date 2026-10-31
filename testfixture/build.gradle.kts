plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.appistan.fixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.appistan.fixture"
        minSdk = 26
        targetSdk = 28
        versionCode = providers.gradleProperty("fixtureVersionCode").orElse("1").get().toInt()
        versionName = "1.0.$versionCode"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
