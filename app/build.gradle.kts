plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Base64

android {
    namespace = "com.overtime.miuix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.overtime.miuix"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
    }

    // 签名配置：CI 从 KEYSTORE_BASE64 环境变量解码生成 keystore 文件
    // 本地开发时在 gradle.properties 中配置 KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    signingConfigs {
        create("release") {
            // CI 环境：KEYSTORE_BASE64 → base64 解码 → 写入临时 keystore 文件
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
            val keystoreFile = if (keystoreBase64 != null && keystoreBase64.isNotBlank()) {
                val decodedBytes = try {
                    // ProcessBuilder 方式执行 base64 -d，兼容 CI 环境
                    val proc = ProcessBuilder("base64", "-d")
                        .redirectInput(ProcessBuilder.Redirect.PIPE)
                        .redirectOutput(ProcessBuilder.Redirect.PIPE)
                        .start()
                    proc.outputStream.use { it.write(keystoreBase64.toByteArray()) }
                    proc.outputStream.close()
                    proc.inputStream.readBytes()
                } catch (e: Exception) {
                    Base64.getDecoder().decode(keystoreBase64)
                }
                val tmpFile = file("${layout.buildDirectory.get().asFile.absolutePath}/tmp/ci-release-keystore.jks")
                tmpFile.parentFile.mkdirs()
                tmpFile.writeBytes(decodedBytes)
                tmpFile
            } else {
                file("release-keystore.jks")
            }
            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: (project.findProperty("KEYSTORE_PASSWORD") as? String) ?: ""
            keyAlias = System.getenv("KEY_ALIAS")
                ?: (project.findProperty("KEY_ALIAS") as? String) ?: ""
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: (project.findProperty("KEY_PASSWORD") as? String) ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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

    // Release APK 输出命名规则：加班记-android-universal-版本号.apk
    androidComponents {
        onVariants(selector().withBuildType("release")) { variant ->
            variant.outputs.forEach { output ->
                (output as? com.android.build.api.variant.impl.VariantOutputImpl)?.let { impl ->
                    impl.outputFileName = "加班记-android-universal-${defaultConfig.versionName}.apk"
                }
            }
        }
    }

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

    // MIUIX
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)

    // NavigationEvent — Miuix OverlayDialog 使用 NavigationBackHandler 需要此依赖
    implementation("androidx.navigationevent:navigationevent-android:1.0.2")
    implementation("androidx.navigationevent:navigationevent-compose-android:1.0.2")

    // 其他
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
