# Reminders 应用设计（跨 macOS + Android）

- 日期：2026-08-11
- 状态：已获用户逐段确认

## 1. 目标

用 Compose Multiplatform 复刻 Apple Reminders 的核心体验，面向 macOS 桌面端 + Android 手机端，纯本地存储。核心诉求：每天极快记录待办（快速输入 + 自然语言日期解析）、设置日期、多列表组织、子任务、搜索、深色模式、回收站。阶段二起加入 WYSIWYG Markdown 备注（支持粘贴图片）。

## 2. 已确认决策

| 主题 | 决策 |
|---|---|
| 开发策略 | 分阶段：先 MVP，后迭代 |
| 存储 | 纯本地（SQLDelight 跨平台 SQLite），无账号、无同步；设计上预留扩展 |
| 通知 | MVP 不含，阶段三做 macOS/Android 本地通知 |
| 界面风格 | 全平台统一复刻 Reminders 风格，一套共享 UI |
| 快捷录入 | 主界面快速输入框 + 自然语言日期解析（中文优先） |
| 列表 | 多自定义列表 + 默认收件箱 |
| MVP 范围 | 完成/未完成、子任务、今日视图、搜索、深色模式、回收站 |
| Markdown 编辑器 | 开源库方案（composables-richtext 评估），阶段二 |
| 架构 | 方案 A：共享 MVVM 三层（data / domain / ui）+ 响应式布局 |

## 3. 总体架构

沿用现有工程结构（shared / desktopApp / androidApp，Kotlin 2.4.10、CMP 1.11.1、AGP 8.13.2、Gradle 8.14.3）。

### 3.1 模块划分

```
shared/
├── data/          # SQLDelight 数据库 + Repository 实现
│   └── TodoDb.sq
├── domain/        # 实体 + 用例（纯 Kotlin，无 Compose 依赖）
│   ├── model/       # TodoItem, TodoList
│   ├── repository/  # TodoRepository 接口
│   └── usecase/     # AddTodo, ToggleComplete, MoveToTrash, ...
├── ui/            # Compose 组件 + ViewModel
│   ├── theme/       # Reminders 风格主题（浅色/深色）
│   ├── navigation/  # NavHost
│   ├── sidebar/  todolist/  detail/  quickadd/  search/
│   └── app/         # RootApp：响应式窗口（宽屏三栏 / 窄屏两栏）
└── util/          # 自然语言日期解析器
```

- 状态管理：androidx lifecycle `ViewModel` + `StateFlow`
- 依赖注入：手动构造器注入（`AppGraph` 根对象），不引入 Koin/Hilt
- 日期：`kotlinx-datetime`

### 3.2 数据模型（SQLDelight）

表 `list`：id (PK), name, color, position, created_at

表 `todo`：id (PK), list_id (FK), title, note, due_date (nullable), is_completed, completed_at, is_trashed, trashed_at, parent_id (nullable, 自引用), sort_position, created_at

索引：
- `(list_id, is_trashed)`：列表视图
- `(is_trashed, due_date)`：今日视图 / 计划视图 / 回收站

规则：
- 回收站 = 软删除（is_trashed=1）；物理清理（30 天）后期实现
- 子任务 = parent_id 自引用；展开/折叠在 UI 层
- 排序 = sort_position 双精度；拖动排序后期迭代，MVP 为创建时间序
- 删除列表 = 其下待办标记删除

### 3.3 平台接入

| 端 | 入口 | 职责 |
|---|---|---|
| desktopApp | main.kt + App.kt | 窗口配置（默认 1000×680、最小宽度 720、标题"提醒事项"）；SQLDelight 用 `sqlite-driver`（JDBC） |
| androidApp | MainActivity | SQLDelight 用 `android-driver`（数据库存 filesDir）；其余全走 shared UI |

两端入口仅组装 `AppGraph`，其余逻辑全部在 shared。

## 4. UI 结构（响应式）

一套共享 UI 代码，按窗口宽度自适应：

**宽窗口（macOS，≥ ~900dp）三栏：**
1. 侧边栏：搜索框、智能列表（今天/计划/全部待办/已完成/垃圾箱）、自定义列表区（＋ 添加列表）
2. 待办列表：标题 + 快速输入框（"＋ 添加待办…"）+ 待办行（完成圆圈、标题、日期徽章、子任务缩进、已完成划线）
3. 详情面板：标题、备注区（阶段二嵌 WYSIWYG 编辑器）、日期行、列表行、子任务

**窄窗口（Android，< ~900dp）两屏：**
1. 主屏：顶部栏 + 快速输入框 + 待办列表 + 底部导航（今天/全部/已完成/更多）
2. 详情页：从列表项点击推送（返回键返回）

**主题**：`isSystemInDarkTheme()` 跟随系统；`MaterialTheme` + 自定义 Reminders 配色（浅色/深色两套 ColorScheme）。

## 5. 快速输入与自然语言日期解析

- 输入框回车即创建待办；`今天`/`明天`/`后天`/`周X`/`下周X`/`X月X日`/`明天15:00`（及英文简版）等片段从标题剥离为 due_date，显示为日期徽章；无匹配则无日期
- 纯正则规则引擎（中文优先），不引入第三方库；解析器为纯函数，可单测

## 6. 视图与功能要点

- **今日视图**：due_date = 今天的待办；**计划视图**：有日期的待办按 过期/今天/明天/即将/计划 分组
- **搜索**：title + note 的 LIKE 查询，实时过滤
- **完成**：点击圆圈切换 is_completed（completed_at 记录时间）
- **回收站**：is_trashed=1 查询；提供"彻底删除"与"恢复"操作
- **子任务**：详情面板中增删，parent_id 关联，列表内缩进展示

## 7. 测试策略

- 数据层：`TodoRepository` 单测（commonTest，内存 SQLite，跑 macOS target）
- 日期解析器：纯函数单测（大量日期用例）
- ViewModel：`kotlinx-coroutines-test` StandardTestDispatcher 测状态流
- UI：MVP 手工验证为主，后续迭代加 Compose UI 测试

## 8. 阶段规划

| 阶段 | 内容 | 里程碑 |
|---|---|---|
| MVP（本次计划） | 3/4/5/6 全部内容 | macOS + Android 双端可日常使用 |
| 阶段二 | WYSIWYG Markdown 编辑器（composables-richtext 评估）、粘贴图片 | 备注升级为富文本 |
| 阶段三 | 系统通知（macOS + Android）、拖动排序、自然语言增强 | |
| 阶段四 | 备份导出/导入、列表颜色自定义、应用图标 | |

## 9. 范围外（明确不做）

- 云同步、多设备同步（预留接口）
- 共享列表、协作
- 位置提醒、标记/旗标、附件文件
- MVP 阶段不做系统通知
