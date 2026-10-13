// ============ app/build.gradle.kts ============
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glassbrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glassbrowser"
        // 关键：minSdk 33（Android 13）。理由见 docs/液态玻璃库评估与选型.md 第五节：
        // · Android 13+ 是 Haze / AGSL / 液态玻璃类库的"最佳环境"（RenderEffect 全速 + AGSL 可用）
        // · 要下探到 24 也不是不行，但模糊会退化，本项目的降级分支已覆盖（GlassTier.Scrim）
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ---------- 液态玻璃（选型结论：Haze 2.0.1）----------
    // ⚠️ 2.x 把模糊实现拆到独立模块 haze-blur，少写这一行会出现"有 hazeEffect 但完全不模糊"
    implementation(libs.haze)
    implementation(libs.haze.blur)

    // ---------- Compose / Material 3 ----------
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)   // ✓ / 齿轮 / 地球
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---------- favicon 加载（带 placeholder/error 兜底，修"裂图"）----------
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ---------- 单测（纯 JVM，不需要设备）----------
    testImplementation(libs.junit)
}
