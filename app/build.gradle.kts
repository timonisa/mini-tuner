plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.independentmusictools.tuner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.independentmusictools.tuner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подпись debug-ключом, чтобы assembleRelease собирался из коробки.
            // Для публикации в магазин настройте собственный ключ подписи.
            signingConfig = signingConfigs.getByName("debug")
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
    }

    testOptions {
        unitTests.all {
            // Обход для Windows с кириллическим путём профиля: Gradle Test Executor
            // не стартует из temp по умолчанию. На других машинах — без эффекта.
            val toolchainTmp = File("C:/toolchain/tmp")
            if (toolchainTmp.isDirectory) {
                it.environment("TMP", toolchainTmp.absolutePath)
                it.environment("TEMP", toolchainTmp.absolutePath)
                it.systemProperty("java.io.tmpdir", toolchainTmp.absolutePath)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
}
