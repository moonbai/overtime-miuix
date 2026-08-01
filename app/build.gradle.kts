plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Base64
import java.util.Properties

// 签名配置：统一从环境变量读取（CI 通过 GitHub Secrets 注入）
// 本地开发回退到 local.properties（已 gitignore，勿把密码写入 gradle.properties）
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signProp(name: String): String? =
    System.getenv(name) ?: localProperties.getProperty(name)

android {
    namespace = "com.overtime.miuix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.overtime.miuix"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.0.6"
        // GitHub Token：从环境变量注入 BuildConfig（避免硬编码到源码被 Push Protection 拦截）
        // CI/本地构建时需设置 GITHUB_TOKEN 环境变量
        buildConfigField("String", "GITHUB_TOKEN", "\"${signProp("GITHUB_TOKEN") ?: ""}\"")
    }

    // 签名配置：环境变量优先（CI），local.properties 回退（本地，已 gitignore）
    signingConfigs {
        create("release") {
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
            if (!keystoreBase64.isNullOrEmpty()) {
                // 延迟写入：doFirst 确保每次构建都重新生成（clean 不会破坏引用）
                val tmpFile = layout.buildDirectory.file("tmp/ci-release-keystore.jks").get().asFile
                storeFile = tmpFile
                storePassword = signProp("KEYSTORE_PASSWORD")
                keyAlias = signProp("KEY_ALIAS")
                keyPassword = signProp("KEY_PASSWORD")
                // 每次签名前重新写入 keystore（解决 clean 后文件丢失问题）
                tasks.whenTaskAdded {
                    if (name.startsWith("validateSigning") || name.startsWith("package") || name == "mergeReleaseResources") {
                        doFirst {
                            tmpFile.parentFile.mkdirs()
                            tmpFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
                        }
                    }
                }
            } else {
                storeFile = file("release-keystore.jks")
                storePassword = signProp("KEYSTORE_PASSWORD")
                keyAlias = signProp("KEY_ALIAS")
                keyPassword = signProp("KEY_PASSWORD")
            }
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
        buildConfig = true
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
    // LocalLifecycleOwner / 生命周期感知的 Compose 工具：日历权限状态实时校验依赖此库
    implementation(libs.androidx.lifecycle.runtime.compose)
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
