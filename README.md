# OvertimeMiuix 加班记

> 简洁实用的加班记录与薪资计算工具，基于 MIUIX 0.9.4 Compose 框架开发。
> 最低支持 Android 13（API 33）。

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.4-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.5.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.2-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-33%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## 功能特性

### 核心功能

- **加班记录管理** — 工作日 / 休息日 / 节假日三种类型；支持请假标记（半天 4H / 全天 8H）与工资自动扣减，加班时长按日期类型设上限
- **实时薪资预览** — 录入时实时计算预估薪资，请假记录显示预计扣减金额
- **快速提报** — 首页一键记录今日加班，自动判定日期类型；常用时长快捷档 + 滑块微调，单次弹窗会话只允许提交一次，杜绝连点重复入库
- **统计报表** — 月度 / 年度视图切换；月度日历卡片（点击日期查看当日明细）与分类统计，年度按月趋势与类型占比
- **智能日期识别** — Uapis 节假日日历（默认）/ MXNZP / 自定义 API 三种数据源自动判定工作日、休息日与节假日，支持调休规则与缓存
- **保存后联动** — 一次保存自动触发推送、日历同步与自动备份；联动失败逐项转为提示，不影响记录入库

### 扩展能力

- **多渠道推送** — 钉钉、飞书、企业微信、WxPusher、Telegram、Discord、自定义 WebHook（结构化 JSON 载荷）
- **日历同步** — 自动将加班记录写入系统日历，支持批量同步
- **备份与恢复** — ZIP 导入 / 导出（`records.json` + `settings.json`）、自动备份（本地 / WebDAV 云端双模式）、WebDAV 手动上传下载
- **MCP 服务** — 内置基于 Ktor 的 Model Context Protocol 服务，AI 助手可通过标准协议读写加班数据
- **应用内更新检查** — GitHub 官方双通道检测，支持匿名访问、APK 直链与 SHA256 一致性校验

### 个性化与交互

- **主题模式** — 跟随系统 / 浅色 / 深色 / Monet 跟随系统 / Monet 浅色 / Monet 深色；全局强调色与三种加班类型标识色均可自定义
- **底栏样式** — 图标+文字 / 仅图标；可切换悬浮胶囊底栏（毛玻璃背景，离底距离 0~64dp 可调），普通底栏同样带高斯模糊
- **返回预测** — 缩放淡入 / 横向滑动 / 纵向滑动 / 纯淡入淡出 / 无动画；横向滑动可指定退出方向（跟随手势 / 恒定向右 / 恒定向左）。手势来源边缘由 Activity 层采集，从屏幕左右两侧返回时「跟随手势」都能正确翻转
- **一致性交互** — 设置开关整行可点击切换；提示条改为偏左限宽显示，避开右下角悬浮加号且不拦截其触摸；删除确认开关、重复点击首页 tab 回到顶部

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| UI 框架 | MIUIX Compose（miuix-ui / miuix-icons / miuix-blur / miuix-preference） | 0.9.4 |
| UI | AndroidX Compose BOM（运行时钉版 1.12.1） | 2026.09.00 |
| 构建 | Android Gradle Plugin | 9.5.1 |
| 构建 | Gradle Wrapper | 9.8.2 |
| 语言 | Kotlin | 2.3.20 |
| 编译 | KSP | 2.3.10 |
| 数据库 | Room | 2.8.4 |
| 存储 | DataStore Preferences | 1.1.1 |
| 导航 | Navigation Compose / NavigationEvent | 2.8.5 / 1.1.2 |
| 生命周期 | Lifecycle / Activity Compose | 2.11.0 / 1.9.3 |
| 网络 | Ktor（CIO Server + SSE + WebSockets + OkHttp Client） | 3.0.3 |
| HTTP | OkHttp | 4.12.0 |
| JSON | Gson | 2.11.0 |
| 协程 | Kotlinx Coroutines | 1.10.1 |
| 序列化 | Kotlinx Serialization | 1.7.3 |

### 编译环境

| 配置项 | 值 |
|--------|-----|
| applicationId / namespace | `com.mars.overtime` |
| compileSdk / targetSdk | 37 |
| minSdk | 33（Android 13，与 miuix-blur 对齐） |
| JVM Target / Source Compatibility | 17 / Java 17 |


### 设计令牌与组件层

- `ui/theme/` 下的 `AppDimens` / `AppShapes` / `AppIconSize` / `AppTypography` 提供全局间距、圆角、图标尺寸与带字重的排版语义别名；设置页原有的 `SettingsDimens` 已委托至 `AppDimens`，单一来源
- `ui/component/OvertimeComponents.kt` 基于 MIUIX 原语组合封装 `AppFab` / `SectionHeader` / `EmptyState` / `Pill`，首页、统计、设置与各二级页复用同一套外观

## 构建与发版

### 本地构建

```bash
# Debug 包（调试签名，不做 R8 压缩，保证编译速度与可调试性）
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# Release 包（R8 代码压缩 + 资源压缩 + 签名 + 产物校验）
./scripts/build-release.sh
# 等价命令：./gradlew assembleRelease
# 脚本额外校验签名、包元信息并输出 SHA256
# 输出：app/build/outputs/apk/release/Overtime-android-universal-<versionName>.apk
```

Release 签名需要 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 四项（环境变量，或写入已被 gitignore 的 `local.properties` 并把 `.jks` 放到 `app/release-keystore.jks`）。`GITHUB_TOKEN` 为可选项，用于把长效只读 PAT 写入 `BuildConfig`，提升应用内更新检查的 API 配额；未配置时走匿名访问。

> 体积优化只在 `release` 生效：AGP 在 `debuggable` 构建下会禁用 R8 优化与混淆，
> 因此 debug 不开启压缩；`proguard-rules.pro` 仅服务于 release（Gson 备份模型
> keep、行号与泛型签名保留、Ktor JVM-only 类放行）。

### CI 自动发版

**GitHub Actions**（`.github/workflows/android.yml`）：`main` 推送与 PR 构建签名 Release，`v*` 标签额外发布 GitHub Release。工作流包含几处针对网络抖动的加固：

- `actions/cache` 持久化 `~/.gradle/caches`（缓存服务异常时跳过，不阻断构建）
- CI 内把 Maven Central 直连镜像插到 `mavenCentral()` 之前，避开 `kotlin-compiler-embeddable`（约 60MB）落到 GitHub Release 资产后返回 504 的路径
- Wrapper 优先使用仓库默认的腾讯云镜像，三次失败后回退官方 Gradle CDN
- Build 步骤最多 3 次重试，且不再使用 `--refresh-dependencies`（避免冷缓存 runner 每次重下依赖）
- 产物门禁：`apksigner` 验签、`aapt` 校验包元信息、体积与 SHA256 写入构建摘要，随后上传 artifact

**CNB 流水线**（`.cnb.yml`）：支持 `main` 推送、Web 手动触发、API 触发、环境自检（`api_trigger_envcheck`）与 `v*` 标签发布；SDK 与 Gradle 依赖走 cow 卷缓存。签名密钥通过「密钥仓库 + `imports`」注入；未配置时降级构建 Debug 包，仅验证编译通过。

GitHub Actions 所需 Secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，以及可选的 `GH_PAT`（长效只读 PAT，不要用短时效的默认 `secrets.GITHUB_TOKEN`）。

## 应用内更新检查

入口：设置 → 关于应用 → 检查更新

- **数据源**：GitHub 官方单源双通道 —— 主通道为 `releases/latest` REST API（ETag 条件请求 + 结果缓存 + 403/429 限流处理），限流或不可达时降级到官方 `releases.atom` 订阅源（不受 REST 匿名配额限制）
- **鉴权**：公开仓库可匿名访问；注入 PAT 后提升到 5000 次/小时
- **即时反馈**：点击后 300ms 内未返回即弹出「正在检查更新…」，结束后精确收起；失败展示具体原因，不会出现「点了没反应」
- **对比**：优先解析发布说明中的 `versionCode`，缺失时逐段比较 `versionName`；有新版本弹窗引导下载 APK 直链（atom 通道回退到发布页）
- **校验**：版本相同但 SHA256 不一致时提示「安装包校验不一致」

## 项目结构

```
app/src/main/java/com/mars/overtime/
├── MainActivity.kt                 # Activity：权限、返回手势边缘采集、NavHost 与转场动画
├── OvertimeApplication.kt          # Application 初始化
├── data/
│   ├── database/                   # Room：AppDatabase / OvertimeDao / OvertimeRecord
│   ├── model/                      # OvertimeType / BottomBarStyle
│   └── repository/                 # OvertimeRepository / SettingsRepository（DataStore）
├── ui/
│   ├── screen/                     # 页面与设置页体系
│   │   ├── MainScreen.kt           # 主框架、底栏、FAB、Snackbar
│   │   ├── HomePage.kt             # 首页（记录列表 + 月度概览）
│   │   ├── StatisticsPage.kt       # 统计（月/年视图 + 日历卡片）
│   │   ├── AddEditRecordPage.kt    # 添加 / 编辑记录
│   │   ├── QuickSubmitSheet.kt     # 快速提报弹窗
│   │   ├── SettingsPage.kt         # 设置中心（无分组标题布局）
│   │   ├── SettingsUi.kt           # 设置项组件（含整行可点击的开关行）
│   │   ├── SettingsGroup.kt        # 统一分组容器
│   │   ├── AppearanceSettingsPage.kt / SalarySettingsPage.kt
│   │   ├── HolidaySettingsPage.kt / CalendarSettingsPage.kt
│   │   ├── BackupSettingsPage.kt / PushSettingsPage.kt / McpSettingsPage.kt
│   │   └── AboutPage.kt            # 关于应用 + 检查更新
│   ├── component/OvertimeComponents.kt  # AppFab / SectionHeader / EmptyState / Pill
│   ├── icon/AppIcons.kt            # 自定义图标
│   ├── snackbar/CustomToast.kt     # 自定义 Toast 与删除 / 检查提示收起逻辑
│   └── theme/                      # Theme.kt + AppDimens / AppShapes / AppIconSize / AppTypography
├── util/                           # SalaryCalculator / HolidayManager / BackupManager /
│                                   # RecordSyncHelper / UpdateChecker / WebDavManager /
│                                   # DataMigrationUtil / FairMemoryManager
├── push/                           # PushManager / PushPayload / CalendarSyncManager
└── mcp/McpHostService.kt           # MCP 服务（Ktor CIO + SSE）
```

## MCP 服务

在「设置 → MCP 服务」启用后可访问，端口默认 `8080`（可修改）：

```json
{
  "mcpServers": {
    "overtime-note": {
      "url": "http://<设备IP>:8080/mcp"
    }
  }
}
```

实现方法：`initialize` / `ping` / `tools/list` / `tools/call`。

支持工具：`add_overtime_record` / `query_overtime_records` / `get_monthly_stats`

## 开源协议

MIT License — 详见 [LICENSE](LICENSE)
