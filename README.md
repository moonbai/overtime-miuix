# OvertimeMiuix 加班记录

> [English Version](README_EN.md)

基于 MIUIX Compose 框架开发的加班记录应用。

## 功能特性

- **加班记录管理** - 支持工作日、周末、节假日加班分类记录
- **实时薪资预览** - 根据基础薪资和倍率实时计算预估薪资
- **统计报表** - 月度/年度加班时长和薪资统计
- **日历同步** - 自动同步加班记录到系统日历
- **智能推送** - 支持钉钉、飞书、WxPusher、Telegram、Discord 等推送渠道
- **节假日管理** - 内置 2026 年节假日数据
- **数据备份** - 本地 JSON 导出/导入，WebDAV 云端同步
- **MCP 服务** - 内置 Model Context Protocol 服务，支持 AI 助手集成
- **个性化设置** - 主题切换、强调色定制、底栏样式可选

## 技术栈

- **UI 框架**: MIUIX Compose (top.yukonga.miuix.kmp)
- **数据库**: Room
- **设置存储**: DataStore Preferences
- **导航**: Navigation Compose
- **网络**: Ktor (MCP Server)
- **构建**: Gradle (Kotlin DSL)

## MCP 服务

启用后可通过以下端点访问数据：

```
GET  /mcp/tools              - 获取可用工具列表
POST /mcp/tools/add_overtime_record - 添加加班记录
POST /mcp/tools/query_overtime_records - 查询加班记录
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

## 构建

```bash
./gradlew assembleDebug
```

APK 输出目录：`app/build/outputs/apk/debug/`

## 项目结构

```
app/src/main/java/com/overtime/miuix/
├── data/
│   ├── database/    # Room 数据库
│   ├── model/       # 数据模型
│   └── repository/  # 数据仓库
├── ui/
│   ├── screen/      # 页面组件
│   └── theme/       # 主题配置
├── util/            # 工具类
└── mcp/             # MCP 服务
```

## 开源协议

MIT License