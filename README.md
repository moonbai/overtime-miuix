# OvertimeMiuix 加班记

> 基于 MIUIX 0.9.4 Compose 框架的加班记录与薪资计算工具，最低支持 Android 13（API 33）。

[![MIUIX](https://img.shields.io/badge/MIUIX-0.9.4-blue)](https://github.com/moonbai/miuix)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.5.1-green)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.8.2-02303A)](https://gradle.org)
[![API](https://img.shields.io/badge/API-33%2B-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

## 功能特性

### 核心功能

- **加班记录管理** — 工作日 / 休息日 / 节假日三种类型；支持请假标记（半天 4H / 全天 8H）与工资自动扣减，加班时长按日期类型设上限
- **实时薪资预览** — 录入时实时计算加班薪资，请假记录显示预计扣减金额
- **快速提报** — 首页一键记录今日加班，自动判定日期类型；常用时长快捷档 + 滑块微调，单次弹窗会话只允许提交一次，杜绝连点重复入库
- **统计报表** — 月度 / 年度视图切换；月度日历卡片（点击日期查看当日明细）与分类统计，年度按月趋势与类型占比
- **智能日期识别** — Uapis 节假日日历（默认）/ MXNZP / 自定义 API 三种数据源自动判定工作日、休息日与节假日，支持调休规则与缓存
- **保存后联动** — 一次保存自动触发推送、日历同步与自动备份；联动失败逐项转为提示，不影响记录入库

### 首页小组件

首页在月度概览卡下方提供 **2 列网格、29 种小组件**，分四类：本月概览 15 项、类型分布 3 项、趋势 7 项、大卡图表 4 项（工时、薪资、次数、日均、类型环形图、月度对比、日历热力图、月度摘要等）。

- **固定尺寸** — 每个组件的 `1×1 / 2×1 / 2×2` 由组件目录定义，**不提供手动调整大小**，从根源上避免缩放导致的文字遮挡；读取旧布局时由 `normalizeLayout()` 自动校正为当前固定尺寸
- **排序与删除** — 首页**长按拖动**直接排序，编辑态点角标删除；管理页不再提供箭头移动
- **添加页只显示未添加项** — 按分类筛选、用真实数据实时预览卡片样式，添加后该项立即从列表消失
- **管理页** — 设置 → 首页小组件，可切换概览卡展示模式、查看已添加组件及固定尺寸、删除并跳转添加页
- **默认布局** — 当月工时 / 当月薪资 / 当月类型 / 当月日均

### 扩展能力

- **多渠道推送** — 钉钉、飞书、企业微信、WxPusher、Telegram、Discord、自定义 WebHook（结构化 JSON 载荷）。飞书以正则识别官方机器人 hook 与企业工作流 webhook（`*.feishu.cn/base/workflow/webhook/event/...`）。官方机器人按协议附加 `msg_type / content.text / timestamp / sign`，企业工作流与自定义地址则把每条信息**平铺为 body 的一个键名**（`date`、`weekday`、`hours`、`amount` 等）。可读文本与日历事件标题中的时长统一为小时制（`0.5H` / `1H` / `1.5H`），旧版「x小时y分钟」/「X.XX小时」文案不再输出，按文本解析的下游需适配；删除日历事件时仍同时匹配新旧标题以清理历史数据
- **日历同步** — 自动将加班记录写入系统日历，支持批量同步
- **备份与恢复** — ZIP 导入 / 导出（`records.json` + `settings.json`）、自动备份（本地 / WebDAV 云端双模式）、WebDAV 手动上传下载。**「导入数据」单一入口**，弹窗内选择来源：设备文件 / 自动备份目录 / 自定义 SAF 目录 / WebDAV 云端；云端入口仅在 WebDAV 配置完整时显示。自动备份落在 `Android/data/com.mars.overtime/files/backup/`（外部存储不可用时回退应用私有目录），文件名 `overtime_backup_yyyyMMdd_HHmmss.zip`
- **MCP 服务** — 内置基于 Ktor 的 Model Context Protocol 服务，AI 助手可通过标准协议读写加班数据（[安全须知](#mcp-服务)）
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

### 依赖管理与版本冲突处理

全部版本集中在 `gradle/libs.versions.toml`，根 `build.gradle.kts` 统一 `apply false`，`app/build.gradle.kts` 只按 alias 引用。几处非直觉的组合都有原因：

| 决策 | 原因 |
|------|------|
| MIUIX 四个依赖全部 `exclude` `org.jetbrains.compose.{foundation,runtime,ui,animation}` | MIUIX 0.9.4 是 Compose Multiplatform 库，其传递依赖的 JetBrains Compose AAR 重打包了 AndroidX Compose 类，与 BOM 提供的类冲突；统一由 AndroidX BOM 管理 |
| 保留 `org.jetbrains.compose.material3` | `material3-window-size-class` 是 MIUIX 运行时依赖 |
| 显式声明 `androidx.collection` | 被 exclude 的 JetBrains Compose 原本传递依赖它 |
| `resolutionStrategy.force` 把全部 `androidx.compose.*` 钉到 **1.12.1** | BOM 2026.09.00 提供 1.12.1，消除传递依赖的版本冲突警告 |
| Kotlin 停在 2.3.20 而非 2.4.0 | 2.4.0 导致 `kotlinBuildToolsApiClasspath` 解析失败；与 KSP 2.3.10 同 minor，保持最小改动 |
| 不留 `kotlin-android` / `material-icons-extended` | 前者由 `kotlin-compose` 插件组合覆盖，后者全项目使用 MIUIX 图标，均无引用 |

> **升级注意**：`force("androidx.compose.*:1.12.1")` 是唯一不在 Version Catalog 内的硬编码版本，升级 Compose 时须同步修改 `app/build.gradle.kts` 与 BOM 两处。
> `settings.gradle.kts` 的 `jitpack.io` 为预留仓库（当前依赖均来自 google / mavenCentral），保留它便于临时接入，但会略微扩大依赖解析面。

### 设计令牌与组件层

- `ui/theme/` 下的 `AppDimens` / `AppShapes` / `AppIconSize` / `AppTypography` 提供全局间距、圆角、图标尺寸与带字重的排版语义别名；`util/` 下的 `AppDefaults` / `AppDateFormats` 提供默认值与日期格式单一来源；设置页原有的 `SettingsDimens` 已委托至 `AppDimens`
- `ui/component/OvertimeComponents.kt` 基于 MIUIX 原语组合封装 `AppFab` / `SectionHeader` / `EmptyState` / `Pill`；`LocalRecords.rememberAppRecords` 为首页与记录页提供同源记录快照，避免切页时闪出「还没有记录」空状态

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
> keep、行号与泛型签名保留、Ktor JVM-only 类放行），必须显式挂载，否则 AGP
> 只应用默认规则，上述规则全部失效。

### CI 自动发版

**GitHub Actions**（`.github/workflows/android.yml`）：`main` 推送与 PR 构建签名 Release，`v*` 标签额外发布 GitHub Release。工作流包含几处针对网络抖动的加固：

- `actions/cache` 持久化 `~/.gradle/caches`（缓存服务异常时跳过，不阻断构建）
- CI 内把 Maven Central 直连镜像插到 `mavenCentral()` 之前，避开 `kotlin-compiler-embeddable`（约 60MB）落到 GitHub Release 资产后返回 504 的路径
- Wrapper 优先使用仓库默认的腾讯云镜像，三次失败后回退官方 Gradle CDN
- Build 步骤最多 3 次重试，且不再使用 `--refresh-dependencies`（避免冷缓存 runner 每次重下依赖）
- 产物门禁：`apksigner` 验签、`aapt` 校验包元信息、体积与 SHA256 写入构建摘要，随后上传 artifact

**CNB 流水线**（`.cnb.yml`）：支持 `main` 推送、Web 手动触发、API 触发、环境自检（`api_trigger_envcheck`）与 `v*` 标签发布；SDK 与 Gradle 依赖走 cow 卷缓存。签名密钥通过「密钥仓库 + `imports`」注入；未配置时降级构建 Debug 包，仅验证编译通过。

GitHub Actions 所需 Secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，以及可选的 `GH_PAT`（长效只读 PAT，不要用短时效的默认 `secrets.GITHUB_TOKEN`）。

## 权限与隐私

应用**启动时不申请任何运行时权限**，仅在用户主动启用对应功能时按需请求（`util/FeaturePermissions.kt` 与各设置页）：

| 权限 | 用途 | 触发时机 |
|------|------|----------|
| `INTERNET` | 推送、WebDAV、节假日 API、更新检查 | 安装即授予（普通权限） |
| `ACCESS_LOCAL_NETWORK` | Android 17（targetSdk≥37）访问局域网，WebDAV / MCP 必需 | 启用局域网功能时 |
| `POST_NOTIFICATIONS` | MCP 前台服务常驻通知 | 启用 MCP 时 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | MCP 前台服务合规声明 | 安装即授予 |
| `READ_CALENDAR` / `WRITE_CALENDAR` | 日历同步 | 启用日历同步时 |
| `REQUEST_INSTALL_PACKAGES` | 应用内下载 APK 后安装 | 用户确认安装时（系统设置页授权） |

备份导入导出走 SAF，不需要存储权限（minSdk 33 下 `READ/WRITE_EXTERNAL_STORAGE` 已废弃，故未声明）。

**隐私须知**：

- 导出的 ZIP 中 `settings.json` **明文包含全部推送 Webhook 地址与密钥、WebDAV 密码、MXNZP `app_secret`、自定义请求头**。备份文件分享或上传云端前请自行评估；自定义备份目录的 SAF URI 授权仅在本机有效，因此不写入备份
- `android:allowBackup="true"`，系统备份 / `adb backup` 会一并带走 DataStore 偏好（含上述凭据）；`dataExtractionRules` 在 Android 12+ 上排除 `datastore` 目录，但系统备份仍可能涉及其他偏好。不希望如此可关闭系统备份，仅使用应用内 ZIP 备份
- 所有外部地址的日志均经 `util/LogUrls.kt` 的 `redactUrl()` 脱敏，只保留协议与主机，避免 webhook token / `app_secret` 进入 logcat

## 项目结构

```
app/src/main/java/com/mars/overtime/
├── MainActivity.kt                 # Activity：权限、返回手势边缘采集、NavHost 与转场动画
├── OvertimeApplication.kt          # Application 初始化
├── data/
│   ├── database/                   # Room：AppDatabase / OvertimeDao / OvertimeRecord
│   ├── model/OvertimeType.kt       # 加班类型枚举
│   └── repository/                 # OvertimeRepository / SettingsRepository（DataStore、
│                                   # WidgetPlacement / HomeWidgetIds / DEFAULT_HOME_LAYOUT）
├── ui/
│   ├── screen/                     # 页面与设置页体系
│   │   ├── MainScreen.kt           # 主框架、底栏、FAB、Snackbar
│   │   ├── HomePage.kt             # 首页：月度概览 + 2 列小组件网格
│   │   ├── HomeWidgetCore.kt       # 小组件目录、固定尺寸、网格排布、聚合与图表
│   │   ├── AddWidgetPage.kt        # 添加组件：仅未添加项 + 分类筛选 + 实时预览
│   │   ├── HomeWidgetsSettingsPage.kt  # 组件管理：概览模式、已添加列表、删除
│   │   ├── RecordsPage.kt / StatisticsPage.kt / AddEditRecordPage.kt
│   │   ├── QuickSubmitSheet.kt     # 快速提报弹窗
│   │   ├── SettingsPage.kt / SettingsUi.kt / SettingsGroup.kt
│   │   ├── AppearanceSettingsPage.kt / SalarySettingsPage.kt
│   │   ├── HolidaySettingsPage.kt / CalendarSettingsPage.kt
│   │   ├── BackupSettingsPage.kt / PushSettingsPage.kt / McpSettingsPage.kt
│   │   └── AboutPage.kt            # 关于应用 + 检查更新
│   ├── component/                  # LocalRecords.kt（共享记录快照）/ OvertimeComponents.kt
│   ├── icon/AppIcons.kt            # 自定义图标
│   ├── snackbar/CustomToast.kt     # 自定义 Toast 与提示收起逻辑
│   └── theme/                      # Theme.kt + AppDimens / AppShapes / AppIconSize / AppTypography
├── util/                           # SalaryCalculator / HolidayManager / BackupManager /
│                                   # BackupSources（自动备份目录与 SAF 目录枚举）/ DataMigrationUtil /
│                                   # WebDavManager / RecordSyncHelper / UpdateChecker /
│                                   # FeaturePermissions（按需权限）/ FairMemoryManager /
│                                   # AppDefaults / AppDateFormats / AppBlur / LogUrls（日志脱敏）
├── push/                           # PushManager / PushPayload / CalendarSyncManager
└── mcp/McpHostService.kt           # MCP 服务（Ktor CIO + SSE + JSON-RPC）
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

> **安全须知**：服务监听 `0.0.0.0`，**当前无鉴权**（`Mcp-Session-Id` 只是会话标识，不是认证）。同一局域网内任何设备都可调用工具读写加班记录，并触发保存后的推送 / 日历 / 备份联动。请仅在可信网络（如家庭局域网）启用，用完及时关闭；不要在有公共 Wi-Fi 的环境长期开启。

## 开源协议

MIT License — 详见 [LICENSE](LICENSE)
