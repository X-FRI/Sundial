<p align="center">
  <img src="docs/assets/sundial-icon.png" width="96" alt="Sundial logo" />
</p>

<h1 align="center">Sundial</h1>

<p align="center">
    使用 Kotlin Compose Multiplatform 实现的全平台同步 Reminder App
</p>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img alt="License" src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" />
  </a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.10-purple.svg?logo=kotlin" />
  <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose_Multiplatform-1.11.1-orange.svg?logo=jetpackcompose" />
  <img alt="Platforms" src="https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop-green.svg" />
  <img alt="Tests" src="https://img.shields.io/badge/Tests-90%20passing-brightgreen.svg" />
</p>

---

<p align="center">
  <img src="docs/assets/promo-hero.png" alt="Sundial desktop and mobile product mockups" />
</p>

## 产品逻辑

Sundial 把待办拆成四个自然步骤：先捕获到收件箱，再安排到今天或未来，随后在工作台按压力分组推进，最后用分析页复盘完成趋势与精力输出。

- **工作台**：默认展示所有未完成事项，并按逾期、今天、未来、无日期、待整理分组展开，第一屏就是当前压力总览。
- **列表**：用于维护真正的清单边界，每个列表都有自己的待办分布和后续分析入口。
- **收件箱**：只负责快速捕获临时事项，提醒用户之后归类或安排，不再混作顶层工作流。
- **分析**：使用图表呈现完成趋势、连续记录、完成率与精力输出，让复盘成为推进动力。
- **同步**：本地优先，可切换 Supabase 多端同步，桌面和移动端共享同一套数据模型。

## 技术栈

| 层 | 技术 |
| --- | --- |
| UI | Compose Multiplatform（共享 UI） |
| 架构 | 分层架构 + Arrow typed effects + 窄端口 use-case 层 |
| 数据库 | SQLDelight（SQLite），本地优先 |
| 同步 | supabase-kt（PostgREST + Realtime），`SyncClient` 端口可插拔 |
| 日期 | kotlinx-datetime |
| 依赖注入 | 手写 `AppGraph` 懒加载图（无框架） |

## 架构

```mermaid
flowchart TB
    subgraph UI["UI 层"]
        Screens["Compose 屏幕"]
        ViewModels["ViewModel / 路由状态机"]
    end

    subgraph Domain["领域层"]
        RepoPort["TodoQueries / TodoCommands"]
        MorePorts["ListCommands / SyncStore / SettingsStore"]
        SyncPort["SyncClient 端口"]
        UseCases["用例"]
    end

    subgraph Data["数据层"]
        Impl["SQLDelight 双写 outbox"]
        LWW["SQL 层 LWW"]
        Noop["NoopSyncClient"]
        Supa["SupabaseSyncClient"]
        Server["Sundial-Server（预留）"]
    end

    DB[("本地 SQLite")]
    Supabase["Supabase（PostgREST + Realtime）"]

    Screens --> ViewModels
    ViewModels --> UseCases
    ViewModels --> RepoPort
    UseCases --> RepoPort
    UseCases --> MorePorts
    RepoPort --> Impl
    MorePorts --> Impl
    Impl --> LWW
    Impl --> DB
    SyncPort --> Noop
    SyncPort --> Supa
    SyncPort --> Server
    Supa <--> Supabase
    Domain -. 依赖注入 .-> Data
```

架构边界简述：

- 核心工作流写入规则通过 use case 调用窄端口，例如工作台/主列表完成切换、日程安排、列表保存、设置保存；简单详情编辑与子任务编辑仍可直接调用命令端口。
- 查询仍保持 `Flow`，服务 Compose 的响应式状态；写命令使用 Arrow `Either` 表达可见错误。
- 同步编排依赖 `SyncStore` + `SyncClient`，运行时生命周期由 Resource/lease 风格模块管理。

同步核心设计：

- 每次本地写入在**同一事务内**更新数据行并追加一条 outbox 记录（完整行 JSON 快照）
- `SyncCoordinator` 批量推送 outbox，按 `seq` 水位线清理——不丢、不重
- 远端变更通过 **SQL 层 LWW**（`updated_at` 大者胜）应用，自身设备回声自动过滤
- `SyncClient` 端口是未来的 seam：新增自建服务器只需实现一个适配器，其余代码零改动

详见 [ADR 0002：多端同步架构决策](docs/adr/0002-sync-backend.md)。

## 快速开始

### 环境要求

- JDK 17+
- Android Studio（Android / iOS 构建）
- Xcode（iOS 构建）

### 运行桌面版

```bash
./gradlew :desktopApp:run
```

### 构建 Android

```bash
./gradlew :androidApp:assembleDebug
```

### 运行测试

```bash
./gradlew :shared:desktopTest :androidApp:assembleDebug
```

### 启用 Supabase 多端同步

1. 在 [Supabase](https://supabase.com) 创建项目
2. 在 SQL Editor 执行 [docs/sync-setup.md](docs/sync-setup.md) 中的初始化脚本
3. 应用内「设置 → Supabase 云端」填入项目 URL 与 anon key，保存即可

> ⚠️ 当前为无登录模型，RLS 对 anon 全开，仅适合个人自用。安全边界详见 [docs/sync-setup.md](docs/sync-setup.md)。

## 项目结构

```
shared/                      共享代码（UI + 领域 + 数据）
  src/commonMain/kotlin/
    com/myapplication/shared/
      domain/                领域模型、仓库端口、同步端口、用例
      data/                  仓库实现、SQLDelight、同步适配器与引擎
      ui/                    Compose 屏幕、组件、主题、ViewModel
      util/                  日期解析与格式化
  src/commonMain/sqldelight/ SQLDelight schema 与迁移
  src/{android,desktop,ios}Main/  平台驱动与入口
desktopApp/                  桌面应用入口
androidApp/                   Android 应用入口
docs/                        架构决策（ADR）、Supabase 配置指南、Logo
TODO.md                      已知问题与待办
```

## 文档

- [ADR 0001：Arrow 类型化错误处理](docs/adr/0001-arrow-functional-core.md)
- [ADR 0002：多端同步架构决策](docs/adr/0002-sync-backend.md)
- [Supabase 同步配置指南](docs/sync-setup.md)
- [已知问题与 Roadmap](TODO.md)

## 已知限制

- 行级 LWW 在双端同时编辑同一行时可能丢更新（个人使用可接受）
- 无墓碑机制：晚到的旧写入可能复活已删除的行
- 拉取为全量（无增量水位线），数据量增大后需换 `since` 增量

## License

[Apache License 2.0](LICENSE.txt)

Copyright © 2026 Somhairle H. Marisol
