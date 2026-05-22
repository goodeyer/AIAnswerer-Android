import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 读取local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use(localProperties::load)
}

// Helper to read properties while providing a default fallback
fun getProperty(key: String, defaultValue: String = ""): String =
    localProperties.getProperty(key) ?: defaultValue

android {
    namespace = "com.hwb.aianswerer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hwb.aianswerer"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += setOf("arm64-v8a")
        }

        // BuildConfig字段 - 从local.properties读取
        val apiUrl = getProperty("api.url", "https://api.openai.com/v1/chat/completions")
        val apiKey = getProperty("api.key", "")
        val apiModel = getProperty("api.model", "gpt-4")
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
        buildConfigField("String", "API_MODEL", "\"$apiModel\"")
    }

    // Release签名配置
    signingConfigs {
        create("release") {
            val storeFile = getProperty("signing.storeFile")
            val storePassword = getProperty("signing.storePassword")
            val keyAlias = getProperty("signing.keyAlias")
            val keyPassword = getProperty("signing.keyPassword")

            if (storeFile.isNotEmpty() && storePassword.isNotEmpty() && keyAlias.isNotEmpty() && keyPassword.isNotEmpty()) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                println("Release signing configuration loaded from local.properties")
            } else {
                println("Warning: Release signing configuration incomplete, using debug key")
            }
        }
    }

    // APK命名规则
    applicationVariants.all {
        val buildTypeName = buildType.name
        val versionNameValue = versionName
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val date = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
            outputImpl.outputFileName =
                "${date}_AIAnswerer_v${versionNameValue}.apk"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true  // 启用R8代码混淆和优化
            isShrinkResources = true  // 启用资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release签名配置
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ML Kit for text recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // OkHttp for HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // implementation("androidx.compose.material:material-icons-extended") // 移除：使用本地图标定义，减少13.1 MB
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(libs.mmkv)

    // Room (RAG 知识库本地存储)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
