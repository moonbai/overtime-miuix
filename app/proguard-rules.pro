# ProGuard rules for OvertimeMiuix
-keep class com.mars.overtime.data.model.** { *; }
-keep class com.mars.overtime.data.database.** { *; }
-keep class com.mars.overtime.mcp.** { *; }
-keep class androidx.room.** { *; }
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-dontwarn io.modelcontextprotocol.**

# ===== 体积优化（正式 release 构建启用 R8 + 资源压缩）=====
# 保留源文件、行号与泛型签名：断点/logcat 行号正常映射，且 Gson 反序列化
# List<OvertimeRecord> 等泛型字段必须依赖 Signature 才能还原元素类型。
-keepattributes SourceFile,LineNumberTable,Signature
-renamesourcefileattribute SourceFile

# Gson 反射目标：备份数据模型与记录实体。混淆字段名或丢失泛型签名
# 会导致备份导入/导出失败（静默丢数据，比崩溃更难排查）。
-keep class com.mars.overtime.util.BackupData { *; }
-keep class com.mars.overtime.util.BackupManager$BackupData { *; }
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Ktor 的 IntellijIdeaDebugDetector 会探测 JVM 调试器，引用 java.lang.management.*
# （Android 运行时不存在这些类，Ktor 内部已有 fallback）。R8 全量模式会把
# 缺失类判为错误，需显式放行，否则 assembleRelease 编译失败。
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
