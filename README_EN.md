# OvertimeMiuix - Overtime Record App

> [中文版](README.md)
>
> An overtime record application built with the MIUIX 0.9.0 Compose framework.

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.0-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.1.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-26%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## Features

- **Overtime Records** — Categorize workday, weekend, and holiday overtime, with time-off/leave flags
- **Real-time Salary Preview** — Estimate salary in real time based on base salary and multipliers
- **Statistics** — Monthly/annual overtime hours and salary statistics with tab switching
- **Calendar Sync** — Auto-sync overtime records to the system calendar
- **Smart Notifications** — Support DingTalk, Feishu, WxPusher, Telegram, Discord, etc.
- **Holiday Management** — Built-in 2026 holiday data
- **Data Backup** — Local JSON export/import, WebDAV cloud sync
- **MCP Service** — Built-in Model Context Protocol server for AI assistant integration
- **Customization** — Theme switching (light/dark/system), accent color, selectable bottom bar style
- **Update Check** — In-app GitHub Release update detection with download guidance (public repo works anonymously; optional PAT raises the rate limit; no plaintext token in source)

## Tech Stack

| Category | Technology | Version |
|----------|------------|---------|
| UI Framework | MIUIX Compose (`miuix-ui` + `miuix-icons-android`) | 0.9.0 |
| Build | Android Gradle Plugin | 9.1.1 |
| Build | Gradle | 9.3.1 |
| Language | Kotlin | 2.3.20 |
| Compile | KSP | 2.3.10 |
| Database | Room | 2.8.4 |
| Storage | DataStore Preferences | 1.1.1 |
| Navigation | Navigation Compose | 2.8.5 |
| Network | Ktor (CIO + SSE + WebSockets) | 3.0.3 |
| MCP | Kotlin SDK (io.modelcontextprotocol) | 0.8.0 |
| JSON | Gson | 2.11.0 |
| Coroutines | Kotlinx Coroutines | 1.10.1 |
| Serialization | Kotlinx Serialization | 1.7.3 |

### Build Environment

| Config | Value |
|--------|-------|
| compileSdk | 37 |
| minSdk | 26 |
| targetSdk | 37 |
| JVM Target | 17 |
| Source Compatibility | Java 17 |

## Build & Release

### Local Build

```bash
# Debug package (debug signing, cannot overwrite a release build on install)
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
| `KEY_ALIAS` | Alias (currently `Mars`) |
| `KEY_PASSWORD` | Key password |

> ⚠️ The keystore file and passwords **must not be committed** to the repo. Provide them only via environment variables / local `local.properties`.

### CI Auto-Release (GitHub Actions)

`.github/workflows/android.yml` listens for `v*` tag pushes and automatically runs "build → Mars signing → publish Release → upload APK":

```bash
# 1. Update versionCode / versionName in app/build.gradle.kts
# 2. Commit and tag to trigger the auto-release
git tag v1.0.7 && git push origin v1.0.7
```

Configure the following in the repo's `Settings → Secrets and variables → Actions`:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | Mars signing info |
| `GH_PAT` | **Long-lived read-only PAT**, injected into the APK as `BuildConfig.GITHUB_TOKEN` for the update checker to access the private repo |

> You **must** use a long-lived read-only PAT (`GH_PAT`). Do **not** use the Actions default `secrets.GITHUB_TOKEN` — it is only valid for the duration of the job and expires immediately after packaging, so it cannot be used for runtime update checks in an installed app.

## In-App Update Check

The in-app update detection is implemented in `util/UpdateChecker.kt`, triggered from **Settings → About → Check for Updates**:

1. **Data Source**: `GET https://api.github.com/repos/moonbai/overtime-miuix/releases/latest`
2. **Auth (optional)**: The repo is public, so `/releases/latest` can be accessed **anonymously** (limit 60 req/hour/IP — enough for manual checks). If a PAT is injected at **compile time** via `BuildConfig.GITHUB_TOKEN`, it is used first for a higher limit (5000 req/hour) and private-repo support (**no plaintext token hardcoded in source**, complying with security rules)
3. **Version Compare**: `compareVersion()` compares the current `versionName` with the Release tag segment by segment; a dialog prompts when a newer version exists
4. **Jump to Download**: The dialog button opens the GitHub Release page via `Intent.ACTION_VIEW`, letting the user complete the download/install (mirror fallback such as `ghproxy` is supported)
5. **Consistency Check**: When versions match but the local and remote SHA256 differ, it warns "installation package verification mismatch" and guides a re-download
6. **Robustness**: API failures are skipped silently; pre-release versions are filtered by default

> Update checking is manually triggered. For "auto-check on launch", add a `LaunchedEffect` calling `UpdateChecker.check()` at an entry point such as `MainScreen`.

## Project Structure

```
app/src/main/java/com/overtime/miuix/
├── data/
│   ├── database/       # Room database (AppDatabase, DAO, Entity)
│   ├── model/          # Data models (OvertimeType, etc.)
│   └── repository/     # Repositories (OvertimeRepository, SettingsRepository)
├── ui/
│   ├── screen/         # Screen components (14 pages)
│   │   ├── MainScreen.kt              # Main frame + bottom navigation
│   │   ├── HomePage.kt                # Home (record list + monthly overview)
│   │   ├── AddEditRecordPage.kt       # Add/edit record
│   │   ├── StatisticsPage.kt          # Statistics report
│   │   ├── SettingsPage.kt            # Settings center
│   │   ├── AppearanceSettingsPage.kt  # Appearance settings
│   │   ├── SalarySettingsPage.kt      # Salary settings
│   │   ├── PushSettingsPage.kt        # Push settings
│   │   ├── BackupSettingsPage.kt      # Backup settings
│   │   ├── CalendarSettingsPage.kt    # Calendar settings
│   │   ├── HolidaySettingsPage.kt     # Holiday management
│   │   ├── McpSettingsPage.kt         # MCP service settings
│   │   └── AboutPage.kt              # About page
│   └── theme/
│       └── Theme.kt                   # Theme config (ThemeController)
├── util/               # Utilities
│   ├── SalaryCalculator.kt            # Salary calculation
│   ├── HolidayManager.kt             # Holiday management
│   └── ...                            # Other utilities
└── mcp/
    └── McpHostService.kt              # MCP service (Ktor Server)
```

## MCP Service

Once enabled, data can be accessed via these endpoints:

```
GET  /mcp/tools                           - List available tools
POST /mcp/tools/add_overtime_record        - Add overtime record
POST /mcp/tools/query_overtime_records     - Query overtime records
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

## Changelog

### v1.0.0 (2026-07-31)

**MIUIX 0.8.8 → 0.9.0 Migration**

- Dependency modularization: monolithic `miuix` → `miuix-ui` + `miuix-icons-android`
- NavigationBar migrated to slot-based API + NavigationBarDisplayMode
- ListItem → BasicComponent (startAction/endActions)
- PreferenceGroup → SmallTitle
- SwitchItem → BasicComponent + Switch
- AlertDialog → OverlayDialog (declarative API)
- ScrollableTabRow → TabRow (tabs + selectedTabIndex)
- Theme API: rememberThemeController() → ThemeController
- Text style updates: headline3→headline2, labelMedium→button, caption→footnote1
- Color updates: onSurfaceVariant → onSurfaceVariantSummary

**Bug Fixes**

- Fixed Flow.collect {} suspension issue (OvertimeRepository + McpHostService)
- Fixed missing McpHostService declaration in AndroidManifest
- Fixed HolidayManager missing SimpleDateFormat import
- Added Room fallbackToDestructiveMigration to avoid DB upgrade crashes
- Fixed preview amount not accounting for isLeave state in time-off mode
- Fixed DataStore state desync on salary settings page
- Fixed MCP service hardcoded salary, now reads from SettingsRepository

**Build System Upgrade**

- AGP 8.13.2 → 9.1.1
- Gradle 8.13 → 9.3.1
- KSP 2.3.9 → 2.3.10
- compileSdk / targetSdk 36 → 37
- Removed kotlin-android plugin (built into AGP 9.0+)

### v1.0.3 ~ v1.0.6

- **v1.0.3**: Switched to the Mars signing keystore, fixed install failure caused by `INSTALL_FAILED_DUPLICATE_PERMISSION` (signature conflict)
- **v1.0.4**: UI optimization and robustness improvements (TopAppBar overlap fix, normal bottom bar glass blur, floating bar button spacing/widening, update package SHA256 consistency check, MCP service crash protection)
- **v1.0.5**: Fixed "check for updates" always failing — switched to PAT auth + OkHttp + segment-by-segment version comparison
- **v1.0.6**: Floating bottom bar changed to a true overlay float (no longer occupies Scaffold reserved space, content can scroll under the bar); release APK naming prefix `加班记 → Overtime` (applies to future releases); completed CI injection of the update-check token and the `REQUEST_INSTALL_PACKAGES` permission

### v1.0.7

- Bottom bar interaction rework: both normal and floating bars are now overlays floating above content; the three screens can scroll under the bar
- Normal bottom bar gains a Gaussian-blur background; the floating bar's blur background now fits exactly the three buttons' footprint (no longer full-width)
- The floating action button (FAB) is moved down to avoid overlapping the floating bar
- Update-check robustness: removed the dead mirror fallback (HTTP 000 and no private-repo auth forwarding), added request timeouts and clear error messages, plus empty-token detection

## License

MIT License — see [LICENSE](LICENSE)
