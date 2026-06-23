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
