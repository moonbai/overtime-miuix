plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.overtime.miuix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.overtime.miuix"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    // 签名配置：优先从环境变量读取，其次从 gradle.properties
    signingConfigs {
        create("release") {
            // KEYSTORE_BASE64 环境变量用于 CI/CD 流水线（GitHub Actions）
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
            if (!keystoreBase64.isNullOrBlank()) {
                val keystoreFile = file("$rootDir/app/release-keystore.jks")
                if (!keystoreFile.exists()) {
                    keystoreFile.parentFile?.mkdirs()
                    // 通过 shell base64 命令解码（避免 Gradle Kotlin DSL 中 java.util 不可用的问题）
                    val process = ProcessBuilder("base64", "-d")
                        .directory(rootDir)
                        .redirectOutput(keystoreFile)
                        .start()
                    process.outputStream.use { it.write(keystoreBase64.toByteArray()) }
                    process.waitFor()
                }
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            } else {
                // 本地开发：检查 gradle.properties 或本地 keystore 文件
                val localKeystore = file("release-keystore.jks")
                if (localKeystore.exists()) {
                    storeFile = localKeystore
                    storePassword = project.findProperty("KEYSTORE_PASSWORD") as? String ?: ""
                    keyAlias = project.findProperty("KEY_ALIAS") as? String ?: ""
                    keyPassword = project.findProperty("KEY_PASSWORD") as? String ?: ""
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val keystoreFile = file("release-keystore.jks")
            if (keystoreFile.exists() || !System.getenv("KEYSTORE_BASE64").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Room KSP 配置
    sourceSets["main"].java.srcDir("build/generated/ksp/main/kotlin")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.navigationevent:navigationevent-compose-android:1.0.2")
    implementation(libs.androidx.datastore.preferences)

    // Room (KSP 处理)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Ktor Server (CIO 引擎 + SSE + WebSockets)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Gson
    implementation(libs.gson)

    // MCP SDK (umbrella 单依赖)
    implementation(libs.mcp.sdk)

    // MIUIX
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)

    // NavigationEvent — Miuix OverlayDialog 使用 NavigationBackHandler 需要此依赖
    implementation("androidx.navigationevent:navigationevent-android:1.0.2")
    implementation("androidx.navigationevent:navigationevent-compose-android:1.0.2")

    // 其他
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
