---
name: Compose Inspector 项目概况
description: 项目定位、模块结构、技术栈、当前进度和待开发计划
type: project
---

## 项目定位
Compose Inspector 是一个 Android 端运行时调试工具，用于在手机上查看 Compose 视图树和布局信息。

## 模块结构
- **inspector-runtime** — 核心库，包含树解析、MVI 状态管理、UI 面板
- **inspector-noop** — 空实现，release 构建零开销
- **sample** — 示例 App（计数器 UI：Count + Increment 按钮）

## 技术栈
- MVI 架构（Intent → Store → StateFlow → UI）
- **LayoutNode 反射解析**（已替代 UiToolingDataApi 方案）— 直接遍历 LayoutNode 树
- AGP 8.2.2 / Kotlin 1.9.22 / Compose BOM 2024.02.00
- minSdk 24 / targetSdk 34 / JVM 17

## 架构演进记录
- 初始方案：CompositionTreeParser 基于 `ui-tooling-data` 的 Group/SlotTable API
- 当前方案：LayoutNodeTreeParser 通过反射直接遍历 AndroidComposeView 的 LayoutNode 树
  - 原因：LayoutNode 能直接访问 Modifier.Node chain，提取 text、padding、size 等信息
  - RecompositionTracker 已重新集成到 LayoutNodeTreeParser，通过内容指纹差异检测 recomposition
  - CompositionTreeParser/SubcompositionResolver 代码仍保留在仓库中，但未使用

## 已完成功能（截至 2026-03-14）
1. **LayoutNodeTreeParser** — 反射解析 LayoutNode 树，提取节点类型、Text 内容、Modifier 详情、布局 bounds
2. **Text 内容展示** — Text 节点显示为 `Text("Count: 0")` 而非 `Text`（通过 TextStringSimpleNode.text 字段提取）
3. **Modifier 详细信息** — 提取 padding/size/fill/background/clip/alpha/clickable/border/offset/shadow 等
4. **双窗口架构** — FAB 窗口（64dp²，右下角）+ Overlay 窗口（全宽 60% 高度，底部）
5. **防 Ripple Crash** — 自定义 ToolbarButton（indication=null）替代 IconButton，配合 disposeComposition() 防止 Samsung 设备崩溃
6. **InspectorStore (MVI)** — 处理 AttachLayoutTree/SelectNode/ToggleExpand/ToggleOverlay
7. **TreeView** — 可展开/折叠的树形列表，带重组计数 badge
8. **NodeDetailPanel** — 节点详情面板（参数、bounds、层级信息）
9. **自动展开前 3 层** — 解析后自动展开树的前 3 层
10. **inspector-noop 模块** — release 零开销
11. **单元测试** — InspectorStore/InspectorTree/RecompositionTracker
12. **Recomposition 计数** — 基于内容指纹差异检测，2 秒自动刷新，TreeView 绿/橙/红 badge 显示

## 提交历史
- `ab2704b` fix: recomposition count always showing 0 — integrate fingerprint-based detection
- `4bd26eb` refactor: remove unused fieldCache and dead CompositionTreeParser code
- `9b2ee1a` feat: extract Text content and Modifier details from LayoutNode tree
- `f30d417` fix: two-window architecture, arrow display, and ripple crash
- `7f37aea` fix: address Code Review feedback — stable IDs, async parsing, subcomposition integration
- `ca659ca` feat: add Compose Inspector core implementation
- `be8fd23` Initial commit: project setup with CLAUDE.md

## 待开发功能（按优先级排序）
1. **搜索/过滤** — 在树中搜索节点名称或文本内容（P1）
2. **节点高亮覆盖层** — 选中节点在 App 中高亮显示边界框（P1）
3. **RecompositionHeatmap** — 重组热力图可视化（P2，当前为空 TODO）
4. **性能剖析** — 重组耗时分析（目前仅有计数）（P2）
5. **状态持久化** — 旋转/进程死亡后恢复 Inspector 状态（P3）
6. **UI/集成测试** — Compose UI 测试覆盖（P3）

## 已知技术限制
- adb input tap 无法触达 TYPE_APPLICATION_PANEL 窗口（自动化测试需要其他方案）
- Modifier.Node chain 的类名在不同 Compose 版本间可能变化（当前适配 BOM 2024.02.00）
- Column/Row 共用 RowColumnMeasurePolicy，无法仅从 measurePolicy 区分（显示为 "Column/Row"）

**Why:** 记录项目全貌和演进历史，便于后续对话快速了解当前进展。
**How to apply:** 新需求到来时，参照此记录判断所处阶段和依赖关系。
