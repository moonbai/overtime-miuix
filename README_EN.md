# OvertimeMiuix - Overtime Record App

> [中文版](README.md)

An overtime record application built with MIUIX Compose framework.

## Features

- **Overtime Records** - Support workday, weekend, and holiday overtime categorization
- **Real-time Salary Preview** - Calculate estimated salary based on base salary and rates
- **Statistics** - Monthly/annual overtime hours and salary statistics
- **Calendar Sync** - Auto-sync overtime records to system calendar
- **Smart Notifications** - Support DingTalk, Feishu, WxPusher, Telegram, Discord, etc.
- **Holiday Management** - Built-in 2026 holiday data
- **Data Backup** - Local JSON export/import, WebDAV cloud sync
- **MCP Service** - Built-in Model Context Protocol server for AI assistant integration
- **Customization** - Theme switching, accent color, bottom bar style options

## Tech Stack

- **UI Framework**: MIUIX Compose (top.yukonga.miuix.kmp)
- **Database**: Room
- **Settings**: DataStore Preferences
- **Navigation**: Navigation Compose
- **Network**: Ktor (MCP Server)
- **Build**: Gradle (Kotlin DSL)

## MCP Service

Enable to access data via these endpoints:

```
GET  /mcp/tools              - List available tools
POST /mcp/tools/add_overtime_record - Add overtime record
POST /mcp/tools/query_overtime_records - Query records
GET  /mcp/tools/get_monthly_stats?month=YYYY-MM - Get monthly stats
```

Config example:
```json
{
  "mcpServers": {
    "overtime": {
      "url": "http://<deviceIP>:8080/mcp"
    }
  }
}
```

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/`

## Project Structure

```
app/src/main/java/com/overtime/miuix/
├── data/
│   ├── database/    # Room database
│   ├── model/       # Data models
│   └── repository/  # Repositories
├── ui/
│   ├── screen/      # Screen components
│   └── theme/       # Theme config
├── util/            # Utilities
└── mcp/             # MCP service
```

## License

MIT License