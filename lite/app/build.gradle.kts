plugins {
    id("com.android.application")
}

android {
    namespace = "com.ai.assistance.operit.quiz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ai.assistance.operit.quiz"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
