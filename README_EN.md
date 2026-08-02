# OvertimeMiuX 加班记

> [中文版](README.md)
>
> A streamlined overtime record & salary calculator app built with the MIUIX 0.9.3 Compose framework.

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.3-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.1.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-26%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## Features

### Core

- **Overtime Records** — Three overtime types (workday / weekend / holiday), with leave flags (half-day / full-day) and automatic salary deduction
- **Real-time Salary Preview** — Live salary estimate while entering data; leave records show estimated deduction
- **Quick Submit** — One-tap today's overtime from home screen with automatic date-type detection and common duration shortcuts
- **Statistics** — Month / year toggle, calendar heatmap + categorized stats, tap any date for daily details
- **Smart Date Detection** — Multiple data sources (Timor / MXNZP / Custom API) auto-detect workdays, weekends, and holidays

### Extended

- **Multi-channel Notifications** — DingTalk, Feishu, WeCom, WxPusher, Telegram, Discord, custom WebHook
- **Calendar Sync** — Auto-sync overtime records to the system calendar with batch sync support
- **Cloud Backup** — WebDAV cloud sync with manual upload / download and auto-backup (local + cloud dual mode)
- **MCP Service** — Built-in Model Context Protocol server for AI assistant data access

### Customization

- **Theme** — Follow system / Light / Dark / Monet (dynamic color) four modes
- **Nav Bar Style** — Normal / Floating styles, both with Gaussian blur frosted-glass effects
- **Type Colors** — Customizable indicator colors for workday / weekend / holiday overtime
- **Update Check** — GitHub + CNB dual-source detection with anonymous access and consistency verification

## Tech Stack

| Category | Technology | Version |
|----------|------------|---------|
| UI Framework | MIUIX Compose (miuix-ui + miuix-icons + miuix-blur) | 0.9.3 |
| Build | Android Gradle Plugin | 9.1.1 |
| Build | Gradle | 9.3.1 |
| Language | Kotlin | 2.4.0 |
| Compile | KSP | 2.3.10 |
| Database | Room | 2.8.4 |
| Storage | DataStore Preferences | 1.1.1 |
| Navigation | Navigation Compose | 2.8.5 |
| Network | Ktor (CIO + SSE + WebSockets + OkHttp) | 3.0.3 |
| HTTP | OkHttp | 4.12.0 |
| JSON | Gson | 2.11.0 |
| Coroutines | Kotlinx Coroutines | 1.10.1 |
| Serialization | Kotlinx Serialization | 1.7.3 |

### Build Environment

| Config | Value |
|--------|-------|
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| JVM Target | 17 |
| Source Compatibility | Java 17 |

## Build & Release

### Local Build

```bash
# Debug package
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release package (Mars signing)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/Overtime-android-universal-<versionName>.apk
```

### Signing Configuration

Release signing is injected via environment variables (CI uses GitHub Secrets; local falls back to `local.properties`, both gitignored):

| Variable | Description |
|----------|-------------|
| `KEYSTORE_BASE64` | Base64 of the Mars keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Alias (`Mars`) |
| `KEY_PASSWORD` | Key password |

> ⚠️ Keystore files and passwords must not be committed.

### CI Auto-Release

`.github/workflows/android.yml` listens for `v*` tag pushes, automatically building → signing → releasing → uploading APK.

Configure signing Secrets and the optional `GH_PAT` (long-lived read-only PAT for in-app update checking) in the repo's `Settings → Secrets and variables → Actions`.

## In-App Update Check

Entry point: Settings → About → Check for Updates

- **Data sources**: GitHub Releases API (primary) → CNB Releases API (fallback)
- **Auth**: Public repos work anonymously; PAT injection raises rate limits
- **Comparison**: Segment-by-segment `versionName` comparison; dialog prompts when a newer version is found
- **Verification**: SHA256 consistency check warns if the local APK doesn't match the official release

## Project Structure

```
app/src/main/java/com/overtime/miuix/
├── data/
│   ├── database/       # Room database (AppDatabase, DAO, Entity)
│   ├── model/          # Data models (OvertimeType, BottomBarStyle)
│   └── repository/     # Repositories (OvertimeRepository, SettingsRepository)
├── ui/
│   ├── screen/         # Screen components (13 pages + 1 sheet)
│   │   ├── MainScreen.kt              # Main frame + bottom nav + FAB
│   │   ├── HomePage.kt                # Home (record list + monthly overview)
│   │   ├── AddEditRecordPage.kt       # Add/edit record
│   │   ├── QuickSubmitSheet.kt        # Quick-submit dialog
│   │   ├── StatisticsPage.kt          # Statistics (calendar + month/year stats)
│   │   ├── SettingsPage.kt            # Settings center
│   │   ├── SettingsGroup.kt           # Unified group wrapper
│   │   ├── AppearanceSettingsPage.kt  # Appearance settings
│   │   ├── SalarySettingsPage.kt      # Base settings (salary/multipliers)
│   │   ├── PushSettingsPage.kt        # Push settings
│   │   ├── BackupSettingsPage.kt      # Backup & restore
│   │   ├── CalendarSettingsPage.kt    # Calendar sync
│   │   ├── HolidaySettingsPage.kt     # Holiday management
│   │   ├── McpSettingsPage.kt         # MCP service
│   │   └── AboutPage.kt              # About page
│   ├── icon/            # Custom icons
│   ├── snackbar/        # Custom toast
│   └── theme/           # Theme config (ThemeController)
├── util/               # Utilities
│   ├── SalaryCalculator.kt            # Salary calculation (overtime + leave deduction)
│   ├── HolidayManager.kt             # Holiday management (multi-source)
│   ├── BackupManager.kt              # Backup management (ZIP import/export)
│   ├── RecordSyncHelper.kt           # Record sync (push + calendar + backup)
│   ├── UpdateChecker.kt              # Update check (GitHub + CNB dual-source)
│   ├── WebDavManager.kt              # WebDAV management
│   └── DataMigrationUtil.kt          # Data migration utilities
├── push/
│   ├── PushManager.kt                # Push channel management
│   └── CalendarSyncManager.kt        # Calendar sync management
└── mcp/
    └── McpHostService.kt              # MCP service (Ktor Server)
```

## MCP Service

Once enabled, data can be accessed via the MCP protocol:

```json
{
  "mcpServers": {
    "overtime-note": {
      "url": "http://<deviceIP>:8080/mcp"
    }
  }
}
```

Supported tools: `add_overtime_record` / `query_overtime_records` / `get_monthly_stats`

## License

MIT License — see [LICENSE](LICENSE)
