plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        versionCode = 12
        versionName = "1.0.11"
        // GitHub Token：从环境变量注入 BuildConfig（避免硬编码到源码被 Push Protection 拦截）
        // CI/本地构建时需设置 GITHUB_TOKEN 环境变量
        buildConfigField("String", "GITHUB_TOKEN", "\"${signProp("GITHUB_TOKEN") ?: ""}\"")
        // CNB Token：作为 GitHub 的兜底更新数据源（cnb.cool），可选；公开仓库可匿名访问
        // CI/本地构建时可设置 CNB_TOKEN 环境变量以提升限额 / 访问私有仓库
        buildConfigField("String", "CNB_TOKEN", "\"${signProp("CNB_TOKEN") ?: ""}\"")
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
                    impl.outputFileName = "Overtime-android-universal-${defaultConfig.versionName}.apk"
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
    implementation(libs.androidx.navigationevent.compose)
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

    // MIUIX 0.9.3 — Compose BOM 已升级至 2026.05.01（Foundation 1.11.2）消除版本冲突。
    // 仅排除 org.jetbrains.compose.foundation（与 AndroidX Foundation 冲突），
    // 保留 org.jetbrains.compose.material3（material3-window-size-class 为 miuix 运行时所需）。
    implementation(libs.miuix.ui) {
        exclude(group = "org.jetbrains.compose.foundation")
    }
    implementation(libs.miuix.icons) {
        exclude(group = "org.jetbrains.compose.foundation")
    }
    implementation(libs.miuix.blur) {
        exclude(group = "org.jetbrains.compose.foundation")
    }
    implementation(libs.miuix.preference) {
        exclude(group = "org.jetbrains.compose.foundation")
    }

    // NavigationEvent — miuix 0.9.3 OverlayDialog 依赖
    implementation("androidx.navigationevent:navigationevent-android:1.1.2")

    // 其他
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}

// 强制统一 Compose Foundation 版本：miuix 0.9.3 通过 JetBrains Compose
// 传递依赖 androidx.compose.foundation:1.11.2，与 BOM 2026.05.01 一致。
// 排除 org.jetbrains.compose.foundation 避免 Duplicate class（JetBrains JAR 内含 AndroidX 类），
// 由 AndroidX BOM + force 统管版本。
configurations.all {
    resolutionStrategy {
        force("androidx.compose.foundation:foundation:1.11.2")
        force("androidx.compose.foundation:foundation-layout:1.11.2")
    }
}
