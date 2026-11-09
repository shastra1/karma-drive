plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// KARMA 앱(스마트폰) 프로토타입 — 동승자 · 가족이 트윈카를 실시간으로 보는 라이브 트윈 · 상처 사유 · 정비소 · 응원
// 화면은 assets/app/index.html (인카 앱이 PC 브라우저에 보여 주는 화면과 같은 코드)
android {
    namespace = "com.karmadrive.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.karmadrive.mobile"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.3.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
