# ProGuard rules for OvertimeMiuix
-keep class com.overtime.miuix.data.model.** { *; }
-keep class com.overtime.miuix.data.database.** { *; }
-keep class com.overtime.miuix.mcp.** { *; }
-keep class androidx.room.** { *; }
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-dontwarn io.modelcontextprotocol.**

# ===== 公平内存/体积优化（debug 也启用 R8）=====
# 保留源文件与行号：压缩后断点与 logcat 行号照常映射
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Gson 反射目标：备份数据模型（BackupData），混淆字段名会导致备份导入失败
-keep class com.overtime.miuix.util.BackupData { *; }
-keep class com.overtime.miuix.util.BackupManager$BackupData { *; }
