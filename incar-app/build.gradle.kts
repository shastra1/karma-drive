import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// API 키는 local.properties에서만 읽는다 (저장소 · 커밋 금지)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localKey(name: String): String = (localProps.getProperty(name) ?: "").trim().replace("\"", "")

android {
    namespace = "com.karmadrive.incar"
    compileSdk = 34

    // AAOS Car API (VHAL 차량 신호 · 주행 제한). SDK Platform 34에 포함된 optional 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.karmadrive.incar"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField("String", "DATA_GO_KR_KEY", "\"${localKey("DATA_GO_KR_KEY")}\"")
        buildConfigField("String", "ITS_API_KEY", "\"${localKey("ITS_API_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // v0.3: Pleos Playground Vehicle SDK — 방향지시등 · 조향 · 안전벨트 · 와이퍼 · 브레이크 · 전조등
    // (PleosVehicleSource가 리플렉션으로 쓰므로, 받지 못하면 이 줄만 지워도 빌드 · 실행됨)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // implementation("ai.pleos.playground:NaviHelper:3.0.5")

    testImplementation("junit:junit:4.13.2")
}
