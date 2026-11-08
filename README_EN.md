# OvertimeMiuX Overtime Tracker

> [中文版](README.md)
>
> A streamlined overtime record & salary calculator built with the MIUIX 0.9.4 Compose framework.
> Requires Android 13 (API 33) or later.

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.4-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.5.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.2-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-33%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## Features

### Core

- **Overtime Records** — Workday / weekend / holiday types, with leave flags (half-day 4h / full-day 8h), automatic salary deduction, and per-type maximum duration
- **Real-time Salary Preview** — Live salary estimate while entering data; leave records show the estimated deduction
- **Quick Submit** — One-tap today's overtime from the home screen with automatic date-type detection, duration presets plus a slider, and a one-shot submit lock that prevents duplicate inserts from rapid taps
- **Statistics** — Month / year views, a monthly calendar card (tap a day for its details), categorized stats, plus yearly monthly trends and type breakdown
- **Smart Date Detection** — Uapis holiday calendar (default) / MXNZP / custom API data sources auto-detect workdays, weekends and holidays, with make-up workday rules and caching
- **Post-save Hooks** — A single save triggers notifications, calendar sync and auto-backup; any hook failure becomes a hint and never rolls back the record

### Extended

- **Multi-channel Notifications** — DingTalk, Feishu, WeCom, WxPusher, Telegram, Discord, custom WebHook (structured JSON payload)
- **Calendar Sync** — Auto-sync overtime records to the system calendar with batch sync support
- **Backup & Restore** — ZIP import / export (`records.json` + `settings.json`), auto-backup (local or WebDAV), and manual WebDAV upload / download
- **MCP Service** — Built-in Ktor-based Model Context Protocol server for AI assistant data access
- **In-app Update Check** — Two official GitHub channels, anonymous access, direct APK link and SHA256 consistency verification

### Customization & Interaction

- **Theme** — Follow system / Light / Dark / Monet Light / Monet Dark; customizable global accent color and per-type indicator colors
- **Nav Bar** — Icon+text or icon-only; optional floating capsule bar with a frosted-glass backdrop and adjustable bottom offset (0–64dp). The regular bar uses Gaussian blur as well
- **Predictive Back** — Scale & fade / horizontal slide / vertical slide / fade / none; horizontal slide can use a fixed exit direction (follow gesture / always right / always left). The gesture's starting edge is captured at the Activity level, so "follow gesture" flips correctly for both left and right swipes
- **Consistent Interaction** — Switch rows are toggled by tapping anywhere on the row; snackbars are offset to the left and width-limited so they never cover or intercept the bottom-right FAB; optional delete confirmation; tapping the Home tab twice scrolls back to top

## Tech Stack

| Category | Technology | Version |
|----------|------------|---------|
| UI Framework | MIUIX Compose (miuix-ui / miuix-icons / miuix-blur / miuix-preference) | 0.9.4 |
| UI | AndroidX Compose BOM (runtime pinned to 1.12.1) | 2026.09.00 |
| Build | Android Gradle Plugin | 9.5.1 |
| Build | Gradle Wrapper | 9.8.2 |
| Language | Kotlin | 2.3.20 |
| Compile | KSP | 2.3.10 |
| Database | Room | 2.8.4 |
| Storage | DataStore Preferences | 1.1.1 |
| Navigation | Navigation Compose / NavigationEvent | 2.8.5 / 1.1.2 |
| Lifecycle | Lifecycle / Activity Compose | 2.11.0 / 1.9.3 |
| Network | Ktor (CIO Server + SSE + WebSockets + OkHttp Client) | 3.0.3 |
| HTTP | OkHttp | 4.12.0 |
| JSON | Gson | 2.11.0 |
| Coroutines | Kotlinx Coroutines | 1.10.1 |
| Serialization | Kotlinx Serialization | 1.7.3 |

### Build Environment

| Config | Value |
|--------|-------|
| applicationId / namespace | `com.mars.overtime` |
| compileSdk / targetSdk | 37 |
| minSdk | 33 (Android 13, aligned with miuix-blur) |
| JVM Target / Source Compatibility | 17 / Java 17 |
| versionName / versionCode | 1.0.0 / 1 (version synced from the tag by the pipelines) |
| Signing | release keystore injected via env vars or `local.properties` |

### Dependency Compatibility (MiuiX 0.9.4)

MIUIX is a Compose Multiplatform library whose AARs repackage AndroidX Compose classes under `org.jetbrains.compose.*`, which collide with the AndroidX BOM. `app/build.gradle.kts` handles this by:

1. Excluding the `foundation` / `runtime` / `ui` / `animation` transitive groups from all four miuix dependencies, while keeping runtime-required pieces such as `material3-window-size-class`
2. Letting AndroidX Compose BOM 2026.09.00 own the versions, with `resolutionStrategy.force` pinning `androidx.compose.*` to **1.12.1** to eliminate version conflicts
3. No version bump, no fork, no Material3 mixing — the MiuiX 0.9.4 dependency stays as-is

### Design Tokens & Components

- `ui/theme/` provides `AppDimens`, `AppShapes`, `AppIconSize` and `AppTypography` for global spacing, corner radii, icon sizes and semantic typography with weights. The settings pages' legacy `SettingsDimens` now delegates to `AppDimens` — a single source of truth
- `ui/component/OvertimeComponents.kt` composes MIUIX primitives into `AppFab` / `SectionHeader` / `EmptyState` / `Pill`, shared by the home, statistics, settings and all sub-pages

## Build & Release

### Local Build

```bash
# Debug package (no R8 shrinking; faster builds, full debuggability)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release package (R8 code + resource shrinking, signing, artifact verification)
./scripts/build-release.sh
# Equivalent: ./gradlew assembleRelease
# The script additionally verifies the signature, package metadata and SHA256
# Output: app/build/outputs/apk/release/Overtime-android-universal-<versionName>.apk
```

Release signing requires `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` (env vars, or the gitignored `local.properties` plus an `.jks` at `app/release-keystore.jks`). `GITHUB_TOKEN` is optional: it embeds a long-lived read-only PAT into `BuildConfig` to raise the in-app update check rate limit; without it the app uses anonymous access.

> Size optimization applies to `release` only: AGP disables R8 optimization and
> obfuscation for `debuggable` builds, so debug no longer enables shrinking.
> `proguard-rules.pro` serves release only (Gson backup model keep rules,
> line-number/generic-signature retention, Ktor JVM-only class allowances).

### CI Auto-Release

**GitHub Actions** (`.github/workflows/android.yml`): builds a signed Release APK on every `main` push and PR; `v*` tags additionally publish a GitHub Release. Several network-flakiness hardening measures are in place:

- `actions/cache` persists `~/.gradle/caches` (cache service errors are non-blocking)
- A direct Maven Central mirror is inserted before `mavenCentral()` inside CI only, avoiding the path where the ~60MB `kotlin-compiler-embeddable` jar gets redirected to a GitHub Release asset and returns 504
- The wrapper prefers the repo's default Tencent Cloud mirror and falls back to the official Gradle CDN after three failures
- The build step retries up to 3 times and no longer uses `--refresh-dependencies` (which forced a full re-download on cold runners)
- Artifact gate: `apksigner` signature check, `aapt` metadata check, size and SHA256 written to the build summary, then artifact upload

**CNB pipeline** (`.cnb.yml`): supports `main` pushes, manual web triggers, API triggers, an environment self-check (`api_trigger_envcheck`) and `v*` tag releases; SDK and Gradle caches use cow volumes. Signing keys come from a secret repository via `imports`; if absent, the pipeline degrades to a Debug build that only verifies compilation.

GitHub Actions Secrets: `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`, plus optional `GH_PAT` (long-lived read-only PAT — do not use the short-lived default `secrets.GITHUB_TOKEN`).

## In-App Update Check

Entry point: Settings → About → Check for Updates

- **Data source**: one official GitHub source, two channels — the `releases/latest` REST API (ETag conditional requests, result caching, 403/429 rate-limit handling), falling back to the official `releases.atom` feed (not subject to the anonymous REST quota) when rate-limited or unreachable
- **Auth**: public repos work anonymously; injecting a PAT raises the limit to 5000 requests/hour
- **Immediate feedback**: a "Checking for updates…" snackbar appears if nothing returns within 300ms and is dismissed precisely when the check finishes; failures show the concrete reason, so the tap never feels dead
- **Comparison**: `versionCode` parsed from the release notes takes priority, falling back to segment-by-segment `versionName`; the dialog links straight to the APK (the atom channel falls back to the release page)
- **Verification**: SHA256 consistency check warns if the local APK doesn't match the official release

## Project Structure

```
app/src/main/java/com/mars/overtime/
├── MainActivity.kt                 # Activity: permissions, back-gesture edge capture, NavHost & transitions
├── OvertimeApplication.kt          # Application initialization
├── data/
│   ├── database/                   # Room: AppDatabase / OvertimeDao / OvertimeRecord
│   ├── model/                      # OvertimeType / BottomBarStyle
│   └── repository/                 # OvertimeRepository / SettingsRepository (DataStore)
├── ui/
│   ├── screen/                     # Screens and the settings component system
│   │   ├── MainScreen.kt           # Main frame, nav bar, FAB, Snackbar
│   │   ├── HomePage.kt             # Home (record list + monthly overview)
│   │   ├── StatisticsPage.kt       # Statistics (month/year views + calendar card)
│   │   ├── AddEditRecordPage.kt    # Add / edit record
│   │   ├── QuickSubmitSheet.kt     # Quick-submit dialog
│   │   ├── SettingsPage.kt         # Settings center (title-less groups)
│   │   ├── SettingsUi.kt           # Settings item components (full-row toggle switches)
│   │   ├── SettingsGroup.kt        # Unified group wrapper
│   │   ├── AppearanceSettingsPage.kt / SalarySettingsPage.kt
│   │   ├── HolidaySettingsPage.kt / CalendarSettingsPage.kt
│   │   ├── BackupSettingsPage.kt / PushSettingsPage.kt / McpSettingsPage.kt
│   │   └── AboutPage.kt            # About page + update check
│   ├── component/OvertimeComponents.kt  # AppFab / SectionHeader / EmptyState / Pill
│   ├── icon/AppIcons.kt            # Custom icons
│   ├── snackbar/CustomToast.kt     # Custom toast + delete / checking snackbar dismissal
│   └── theme/                      # Theme.kt + AppDimens / AppShapes / AppIconSize / AppTypography
├── util/                           # SalaryCalculator / HolidayManager / BackupManager /
│                                   # RecordSyncHelper / UpdateChecker / WebDavManager /
│                                   # DataMigrationUtil / FairMemoryManager
├── push/                           # PushManager / PushPayload / CalendarSyncManager
└── mcp/McpHostService.kt           # MCP service (Ktor CIO + SSE)
```

## MCP Service

Enable it under Settings → MCP Service; the default port is `8080` (configurable):

```json
{
  "mcpServers": {
    "overtime-note": {
      "url": "http://<deviceIP>:8080/mcp"
    }
  }
}
```

Implemented methods: `initialize` / `ping` / `tools/list` / `tools/call`.

Supported tools: `add_overtime_record` / `query_overtime_records` / `get_monthly_stats`

## License

MIT License — see [LICENSE](LICENSE)
