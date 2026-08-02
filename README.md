# OvertimeMiuix 加班记

> 简洁实用的加班记录与薪资计算工具，基于 MIUIX 0.9.3 Compose 框架开发。

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.3-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.1.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-26%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## 功能特性

### 核心功能

- **加班记录管理** — 工作日 / 周末 / 节假日三种类型，支持请假标记（半天/全天）与工资自动扣减
- **实时薪资预览** — 录入时实时计算预估薪资，请假记录显示预计扣减金额
- **快速提报** — 首页一键记录今日加班，自动判定日期类型，常用时长快捷选择
- **统计报表** — 月度/年度切换，日历热力图 + 分类统计，点击日期查看当日明细
- **智能日期识别** — 多数据源（Timor / MXNZP / 自定义 API）自动判定工作日/休息日/节假日

### 扩展能力

- **多渠道推送** — 支持钉钉、飞书、企业微信、WxPusher、Telegram、Discord、自定义 WebHook
- **日历同步** — 自动将加班记录写入系统日历，支持批量同步
- **云端备份** — WebDAV 云端同步，支持手动上传/下载与自动备份（本地 + 云端双模式）
- **MCP 服务** — 内置 Model Context Protocol 服务，AI 助手可通过标准协议访问加班数据

### 个性化

- **主题切换** — 跟随系统 / 浅色 / 深色 / Monet 取色四种模式
- **底栏样式** — 普通 / 悬浮两种样式，均支持高斯模糊毛玻璃效果
- **类型配色** — 工作日/周末/节假日加班可自定义标识颜色
- **更新检查** — GitHub + CNB 双源检测，支持匿名访问与一致性校验

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| UI 框架 | MIUIX Compose（miuix-ui + miuix-icons + miuix-blur） | 0.9.3 |
| 构建 | Android Gradle Plugin | 9.1.1 |
| 构建 | Gradle | 9.3.1 |
| 语言 | Kotlin | 2.4.0 |
| 编译 | KSP | 2.3.10 |
| 数据库 | Room | 2.8.4 |
| 存储 | DataStore Preferences | 1.1.1 |
| 导航 | Navigation Compose | 2.8.5 |
| 网络 | Ktor（CIO + SSE + WebSockets + OkHttp） | 3.0.3 |
| HTTP | OkHttp | 4.12.0 |
| JSON | Gson | 2.11.0 |
| 协程 | Kotlinx Coroutines | 1.10.1 |
| 序列化 | Kotlinx Serialization | 1.7.3 |

### 编译环境

| 配置项 | 值 |
|--------|-----|
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| JVM Target | 17 |
| Source Compatibility | Java 17 |

## 构建与发版

### 本地构建

```bash
# Debug 包（调试签名）
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# Release 包（Mars 签名）
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/Overtime-android-universal-<versionName>.apk
```

### 签名配置

Release 签名通过环境变量注入（CI 走 GitHub Secrets，本地回退 `local.properties`，均已 gitignore）：

| 变量 | 说明 |
|------|------|
| `KEYSTORE_BASE64` | Mars keystore 的 Base64 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名（`Mars`） |
| `KEY_PASSWORD` | 密钥密码 |

> ⚠️ 签名文件与密码禁止提交仓库。

### CI 自动发版

`.github/workflows/android.yml` 监听 `v*` 标签推送，自动编译 → 签名 → 发布 Release → 上传 APK。

需在仓库 `Settings → Secrets and variables → Actions` 配置签名相关 Secrets，以及可选的 `GH_PAT`（长效只读 PAT，供应用内更新检查使用）。

## 应用内更新检查

入口：设置 → 关于 → 检查更新

- **数据源**：GitHub Releases API（优先）→ CNB Releases API（兜底）
- **鉴权**：公开仓库可匿名访问；注入 PAT 后提升限额
- **对比**：逐段比较 `versionName`，有新版本弹窗引导下载
- **校验**：版本相同但 SHA256 不一致时提示"安装包校验不一致"

## 项目结构

```
app/src/main/java/com/overtime/miuix/
├── data/
│   ├── database/       # Room 数据库（AppDatabase, DAO, Entity）
│   ├── model/          # 数据模型（OvertimeType, BottomBarStyle）
│   └── repository/     # 数据仓库（OvertimeRepository, SettingsRepository）
├── ui/
│   ├── screen/         # 页面组件（13 个页面 + 1 个弹窗）
│   │   ├── MainScreen.kt              # 主框架 + 底部导航 + FAB
│   │   ├── HomePage.kt                # 首页（记录列表 + 月度概览）
│   │   ├── AddEditRecordPage.kt       # 添加/编辑记录
│   │   ├── QuickSubmitSheet.kt        # 快速提报弹窗
│   │   ├── StatisticsPage.kt          # 统计报表（日历 + 月/年统计）
│   │   ├── SettingsPage.kt            # 设置中心
│   │   ├── SettingsGroup.kt           # 统一分组容器
│   │   ├── AppearanceSettingsPage.kt  # 外观设置
│   │   ├── SalarySettingsPage.kt      # 基础设置（薪资/倍率）
│   │   ├── PushSettingsPage.kt        # 推送设置
│   │   ├── BackupSettingsPage.kt      # 备份与恢复
│   │   ├── CalendarSettingsPage.kt    # 日历同步
│   │   ├── HolidaySettingsPage.kt     # 节假日管理
│   │   ├── McpSettingsPage.kt         # MCP 服务
│   │   └── AboutPage.kt              # 关于应用
│   ├── icon/            # 自定义图标
│   ├── snackbar/        # 自定义 Toast
│   └── theme/           # 主题配置（ThemeController）
├── util/               # 工具类
│   ├── SalaryCalculator.kt            # 薪资计算（加班 + 请假扣减）
│   ├── HolidayManager.kt             # 节假日管理（多数据源）
│   ├── BackupManager.kt              # 备份管理（ZIP 导入导出）
│   ├── RecordSyncHelper.kt           # 记录同步（推送 + 日历 + 备份）
│   ├── UpdateChecker.kt              # 更新检查（GitHub + CNB 双源）
│   ├── WebDavManager.kt              # WebDAV 管理
│   └── DataMigrationUtil.kt          # 数据迁移工具
├── push/
│   ├── PushManager.kt                # 推送渠道管理
│   └── CalendarSyncManager.kt        # 日历同步管理
└── mcp/
    └── McpHostService.kt              # MCP 服务（Ktor Server）
```

## MCP 服务

启用后可通过 MCP 协议访问数据：

```json
{
  "mcpServers": {
    "overtime-note": {
      "url": "http://<设备IP>:8080/mcp"
    }
  }
}
```

支持工具：`add_overtime_record` / `query_overtime_records` / `get_monthly_stats`

## 开源协议

MIT License — 详见 [LICENSE](LICENSE)
