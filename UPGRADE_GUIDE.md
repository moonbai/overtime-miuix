# 升级指南：MIUIX 0.9.4 + Android 17 适配

生成于 2026-11-07。全部新版文件在 **`upgrade-0.9.4/`** 目录中。
系统限制我不能覆盖项目原有文件，需你手动复制替换（约 1 分钟）。

## 一、改了什么

**1. upgrade-0.9.4/libs.versions.toml**
- miuix 0.9.3 → **0.9.4**（2026-09-20 发布；ui/icons/blur/preference 四个子模块已在 Maven Central 核实存在）
- composeBom 2026.04.01 → **2026.09.00**（提供 Compose 1.12.1；miuix 0.9.4 编译基线 1.12.0，必须配套，否则 force 会把库降级到 1.11.2 运行 → NoSuchMethodError）
- lifecycleRuntimeKtx 2.8.7 → **2.11.0**（miuix 0.9.4 传递 2.11.0，原声明本就会被解析成 2.11.0，显式对齐）
- kotlin 2.4.0 / ksp 2.3.10 **未动**：AGP 9.1.1 内置 Kotlin，compose/serialization 插件须与其编译器一致，盲升有兼容风险。若出现 kotlin metadata「compiled with a newer version」警告，再升 kotlin 2.4.20 并验证 KSP。

**2. upgrade-0.9.4/build.gradle.kts**
- force 块 12 项 1.11.2 → **1.12.1**；注释同步（0.9.4 / BOM 2026.09.00）；exclude + 保留 material3 策略不变

**3. upgrade-0.9.4/AndroidManifest.xml**
- 新增 **ACCESS_LOCAL_NETWORK**：Android 17 上 targetSdk ≥ 37 后访问局域网（WebDAV 备份、MCP 局域网接入）必需，未授权 BPF 静默丢包
- blur 注释修正：0.9.4 的 AAR minSdk 实为 33（原注释 31），overrideLibrary 仍保留且有效

**4. upgrade-0.9.4/MainActivity.kt**
- 启动时：API ≥ 37 且未授权 → 请求 ACCESS_LOCAL_NETWORK（一次性；已授权/已拒绝不再打扰；永久拒绝后到 系统设置→应用→权限 手动开）

## 二、替换步骤（AndroidIDE 内置终端执行）

```bash
cd /sdcard/AndroidIDEProjects/overtime-miuix
mkdir -p .backup-v1.1.2
cp gradle/libs.versions.toml .backup-v1.1.2/
cp app/build.gradle.kts .backup-v1.1.2/
cp app/src/main/AndroidManifest.xml .backup-v1.1.2/
cp app/src/main/java/com/overtime/miuix/MainActivity.kt .backup-v1.1.2/
cp upgrade-0.9.4/libs.versions.toml gradle/libs.versions.toml
cp upgrade-0.9.4/build.gradle.kts app/build.gradle.kts
cp upgrade-0.9.4/AndroidManifest.xml app/src/main/AndroidManifest.xml
cp upgrade-0.9.4/MainActivity.kt app/src/main/java/com/overtime/miuix/MainActivity.kt
rm -f gradle/libs.versions.toml.new app/src/main/AndroidManifest.xml.new app/build.gradle.kts.new app/src/main/java/com/overtime/miuix/MainActivity.kt.new
```
（最后一条 `rm -f` 清掉我早前失败尝试留下的 4 个 `*.new` 残留文件；你手动删也行。）

## 三、构建验证

AndroidIDE 中 Build → assembleDebug，预期：
- 依赖解析无冲突告警；foundation/runtime/ui 均为 1.12.1，miuix 全家 0.9.4，编译 0 报错
- 若报 Compose Compiler 插件与 Kotlin 不匹配 → kotlin 升 2.4.20 后重试
- 若报 Room KSP 错误 → 检查 ksp 2.3.10 与 kotlin 的匹配性

## 四、回归清单

- [ ] 六个页面打开正常，底部毛玻璃底栏正常（API 33+ 设备）
- [ ] 对话框圆角：0.9.4 起居中对话框固定 32dp（不再继承屏幕圆角），过一眼视觉
- [ ] 日历权限状态校验（LocalLifecycleOwner / lifecycle 2.11.0）
- [ ] Android 17 真机：首启弹本地网络授权 → WebDAV 局域网备份、MCP 连接
- [ ] 推送 WebHook、节假日 API、更新检查、转屏表单（17 上转屏后键盘不再自动恢复，属系统行为）

## 五、可选（纯文案，不阻塞）
- README.md / README_EN.md 徽章 0.9.3 → 0.9.4
- ui/icon/AppIcons.kt 注释「锁定 miuix 0.9.0」是过期注释，可顺手更新
- 如发版：versionCode / versionName 升版
