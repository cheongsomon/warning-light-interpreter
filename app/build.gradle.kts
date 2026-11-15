import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 비밀값은 local.properties(커밋 금지)에서 읽는다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// 공개 배포용 APK(-PpublicBuild)에는 비밀값을 넣지 않는다.
val publicBuild = project.hasProperty("publicBuild")
fun secret(key: String) = if (publicBuild) "\"\"" else "\"${localProps.getProperty(key, "")}\""

android {
    namespace = "ai.pleos.warninglight"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.pleos.warninglight"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PLEOS_CLIENT_ID", secret("pleos.clientId"))
        buildConfigField("String", "PLEOS_CLIENT_SECRET", secret("pleos.clientSecret"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 데모 배포용 APK: 디버그 키로 서명해 바로 설치 가능하게 한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
