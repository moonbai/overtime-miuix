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
- **更新检查** — 应用内检测 GitHub Release 新版本并引导下载（公开仓库匿名可查，可选 PAT 提升限额，源码无明文 Token）

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

## 构建与发版

### 本地构建

```bash
# Debug 包（调试签名，不能覆盖安装正式包）
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# Release 包（Mars 签名）
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/Overtime-android-universal-<versionName>.apk
```

### 签名配置

Release 签名通过环境变量注入（CI 走 GitHub Secrets，本地回退 `local.properties`，二者均已 gitignore）：

| 变量 | 说明 |
|------|------|
| `KEYSTORE_BASE64` | Mars keystore 的 Base64 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名（当前为 `Mars`） |
| `KEY_PASSWORD` | 密钥密码 |

> ⚠️ 签名文件与密码**禁止提交仓库**，仅通过环境变量 / 本地 `local.properties` 提供。

### CI 自动发版（GitHub Actions）

`.github/workflows/android.yml` 监听 `v*` 标签推送，自动完成「编译 → Mars 签名 → 发布 Release → 上传 APK」：

```bash
# 1. 修改 app/build.gradle.kts 的 versionCode / versionName
# 2. 提交并打标签即触发自动发版
git tag v1.0.7 && git push origin v1.0.7
```

需在仓库 `Settings → Secrets and variables → Actions` 配置：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | Mars 签名信息 |
| `GH_PAT` | **长效只读 PAT**，注入 APK 内 `BuildConfig.GITHUB_TOKEN`，供更新检查访问私有仓库 |

> 必须使用**长效只读 PAT**（`GH_PAT`），不要使用 Actions 默认 `secrets.GITHUB_TOKEN`——它仅在任务运行期间有效，打包后即失效，无法用于已安装 App 的运行时更新检查。

## 应用内更新检查

应用内更新检测由 `util/UpdateChecker.kt` 实现，入口在「设置 → 关于 → 检查更新」：

1. **数据源**：`GET https://api.github.com/repos/moonbai/overtime-miuix/releases/latest`
2. **鉴权（可选）**：仓库为公开仓库，可**匿名**直接访问 `/releases/latest`（限额 60 次/小时/IP，手动检查足够）；若编译期经 `BuildConfig.GITHUB_TOKEN` 注入了 PAT，则优先使用令牌以获得更高限额（5000 次/小时）并兼容私有仓库（**源码不硬编码明文**，符合安全规范）
3. **版本对比**：`compareVersion()` 逐段比较当前 `versionName` 与 Release tag，有新版本则弹窗提示
4. **跳转下载**：弹窗按钮通过 `Intent.ACTION_VIEW` 打开 GitHub Release 页面，由用户完成下载 / 安装（支持 `ghproxy` 等镜像兜底）
5. **一致性校验**：版本相同但本地与远端 SHA256 不一致时，提示「安装包校验不一致」并引导重新下载
6. **健壮性**：API 失败静默跳过；预发布版本默认过滤

> 更新检查为手动触发；若需「启动自动检测」，可在 `MainScreen` 等入口补充 `LaunchedEffect` 调用 `UpdateChecker.check()`。

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

### v1.0.3 ~ v1.0.6

- **v1.0.3**：改用 Mars 签名 keystore，修复 `INSTALL_FAILED_DUPLICATE_PERMISSION`（签名冲突）导致的安装失败
- **v1.0.4**：UI 优化与健壮性增强（TopAppBar 重叠修复、普通底栏毛玻璃、悬浮底栏按钮间距/加宽、更新包 SHA256 一致性校验、MCP 服务崩溃防护）
- **v1.0.5**：修复「检查更新」始终无效——改用 PAT 鉴权 + OkHttp + 版本号逐段比较
- **v1.0.6**：悬浮底栏改为真正叠加层悬浮（不再占用 Scaffold 预留空间，内容可滚动至底栏之下）；发版 APK 命名前缀 `加班记 → Overtime`（后续发版生效）；补全 CI 注入更新检查 Token 与 `REQUEST_INSTALL_PACKAGES` 权限

### v1.0.7

- 底栏交互重构：普通 / 悬浮底栏均改为叠加层浮于内容之上，三个页面可滚动至底栏之下
- 普通底栏补充高斯模糊背景；悬浮底栏模糊背景贴合三个按钮占位（不再整行铺满）
- 悬浮按钮（FAB）下移，避免与悬浮底栏重叠
- 更新检查健壮性：移除失效镜像兜底（HTTP 000 且不转发私有仓库鉴权）、增加请求超时与明确错误提示、空令牌检测

### v1.0.8

- 更新检查支持公开仓库匿名访问：无需配置令牌即可检查更新（GitHub 公开仓库 `releases/latest` 匿名返回 200）；令牌改为可选，仅用于提升速率限额与私有仓库
- 检测顺序：优先令牌 → 匿名请求 → 公开镜像兜底；明确区分 404（仓库不可访问）/ 403（速率限额）/ 401（令牌无效）错误提示

### v1.0.9

- 普通底栏恢复为原有行为（Scaffold 预留空间），并叠加高斯模糊背景；内容不再被底栏遮挡
- 修复设置「关于」等内容被底栏遮挡：首页 / 统计 / 设置三个界面统一补充底部留白，均可滚动至底栏之上
- 悬浮底栏高斯模糊背景与悬浮底栏完全一致（位置与尺寸贴合，不再多出空白毛玻璃区域）

### v1.0.10

- 悬浮底栏改为「彻底悬浮」：内容可滚动至底栏背后（毛玻璃透出其后内容），各页面列表补充底部留白，保证末项（如「关于」）可滚动至悬浮栏之上、不被遮挡
- 更新检查升级为 GitHub / CNB 双源：GitHub（令牌 → 匿名 → 镜像）全部失败后追加 CNB（cnb.cool）兜底，防止单边网络 / 区域不可达
- CI 注入可选 CNB_TOKEN 供兜底数据源鉴权（公开仓库可匿名访问）

## 开源协议

MIT License — 详见 [LICENSE](LICENSE)
