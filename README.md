# OvertimeMiuix 加班记录

> 基于 MIUIX 0.9.0 Compose 框架开发的加班记录应用

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.0-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.1.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-26%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## 功能特性

- **加班记录管理** — 支持工作日、周末、节假日加班分类记录，含调休/请假标记
- **实时薪资预览** — 根据基础薪资和倍率实时计算预估薪资
- **统计报表** — 月度/年度加班时长和薪资统计，Tab 页切换
- **日历同步** — 自动同步加班记录到系统日历
- **智能推送** — 支持钉钉、飞书、WxPusher、Telegram、Discord 等推送渠道
- **节假日管理** — 内置 2026 年节假日数据
- **数据备份** — 本地 JSON 导出/导入，WebDAV 云端同步
- **MCP 服务** — 内置 Model Context Protocol 服务，支持 AI 助手集成
- **个性化设置** — 主题切换（亮/暗/跟随系统）、强调色定制、底栏样式可选

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| UI 框架 | MIUIX Compose (`miuix-ui` + `miuix-icons-android`) | 0.9.0 |
| 构建 | Android Gradle Plugin | 9.1.1 |
| 构建 | Gradle | 9.3.1 |
| 语言 | Kotlin | 2.3.20 |
| 编译 | KSP | 2.3.10 |
| 数据库 | Room | 2.8.4 |
| 存储 | DataStore Preferences | 1.1.1 |
| 导航 | Navigation Compose | 2.8.5 |
| 网络 | Ktor (CIO + SSE + WebSockets) | 3.0.3 |
| MCP | Kotlin SDK (io.modelcontextprotocol) | 0.8.0 |
| JSON | Gson | 2.11.0 |
| 协程 | Kotlinx Coroutines | 1.10.1 |
| 序列化 | Kotlinx Serialization | 1.7.3 |

### 编译环境

| 配置项 | 值 |
|--------|-----|
| compileSdk | 37 |
| minSdk | 26 |
| targetSdk | 37 |
| JVM Target | 17 |
| Source Compatibility | Java 17 |

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# 输出目录
# app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/overtime/miuix/
├── data/
│   ├── database/       # Room 数据库 (AppDatabase, DAO, Entity)
│   ├── model/          # 数据模型 (OvertimeType 等)
│   └── repository/     # 数据仓库 (OvertimeRepository, SettingsRepository)
├── ui/
│   ├── screen/         # 页面组件 (14 个页面)
│   │   ├── MainScreen.kt              # 主框架 + 底部导航
│   │   ├── HomePage.kt                # 首页 (记录列表 + 月度概览)
│   │   ├── AddEditRecordPage.kt       # 添加/编辑记录
│   │   ├── StatisticsPage.kt          # 统计报表
│   │   ├── SettingsPage.kt            # 设置中心
│   │   ├── AppearanceSettingsPage.kt  # 外观设置
│   │   ├── SalarySettingsPage.kt      # 薪资设置
│   │   ├── PushSettingsPage.kt        # 推送设置
│   │   ├── BackupSettingsPage.kt      # 备份设置
│   │   ├── CalendarSettingsPage.kt    # 日历设置
│   │   ├── HolidaySettingsPage.kt     # 节假日管理
│   │   ├── McpSettingsPage.kt         # MCP 服务设置
│   │   └── AboutPage.kt              # 关于页面
│   └── theme/
│       └── Theme.kt                   # 主题配置 (ThemeController)
├── util/               # 工具类
│   ├── SalaryCalculator.kt            # 薪资计算
│   ├── HolidayManager.kt             # 节假日管理
│   └── ...                            # 其他工具
└── mcp/
    └── McpHostService.kt              # MCP 服务 (Ktor Server)
```

## MCP 服务

启用后可通过以下端点访问数据：

```
GET  /mcp/tools                           - 获取可用工具列表
POST /mcp/tools/add_overtime_record        - 添加加班记录
POST /mcp/tools/query_overtime_records     - 查询加班记录
GET  /mcp/tools/get_monthly_stats?month=YYYY-MM - 获取月度统计
```

配置示例：

```json
{
  "mcpServers": {
    "overtime": {
      "url": "http://<设备IP>:8080/mcp"
    }
  }
}
```

## 变更记录

### v1.0.0 (2026-07-31)

**MIUIX 0.8.8 → 0.9.0 迁移**

- 依赖模块化：单体 `miuix` → `miuix-ui` + `miuix-icons-android`
- NavigationBar 迁移至 slot-based API + NavigationBarDisplayMode
- ListItem → BasicComponent (startAction/endActions)
- PreferenceGroup → SmallTitle
- SwitchItem → BasicComponent + Switch
- AlertDialog → OverlayDialog (声明式 API)
- ScrollableTabRow → TabRow (tabs + selectedTabIndex)
- Theme API：rememberThemeController() → ThemeController
- 文本样式更新：headline3→headline2, labelMedium→button, caption→footnote1
- 颜色更新：onSurfaceVariant → onSurfaceVariantSummary

**Bug 修复**

- 修复 Flow.collect {} 挂起问题（OvertimeRepository + McpHostService）
- 修复 AndroidManifest 缺少 McpHostService 声明
- 修复 HolidayManager 缺少 SimpleDateFormat import
- 添加 Room fallbackToDestructiveMigration 避免数据库升级崩溃
- 修复调休模式下预览金额未考虑 isLeave 状态
- 修复工资设置页 DataStore 状态不同步
- 修复 MCP 服务薪资硬编码，改为读取 SettingsRepository

**构建系统升级**

- AGP 8.13.2 → 9.1.1
- Gradle 8.13 → 9.3.1
- KSP 2.3.9 → 2.3.10
- compileSdk / targetSdk 36 → 37
- 移除 kotlin-android 插件（AGP 9.0+ 已内置）

## 开源协议

MIT License — 详见 [LICENSE](LICENSE)
