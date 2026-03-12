# Compose Inspector

## 团队协作流程

本项目采用 4 角色协作模式，通过 Claude Code Skills 驱动：

| 角色 | Skill | 职责 |
|------|-------|------|
| 架构师 | `@architect` | 技术选型、方案设计、全局协调、最终交付 |
| 效能工程师 | `@infra` | 工具研发、基建沉淀、CI/CD、自动化支持 |
| 开发工程师 | `@coder` | 业务逻辑与 UI 代码实现（Compose / UIKit） |
| 测试专家 | `@tester` | 质量保障、测试编写、Code Review、回归测试 |

## 标准工作流（8 阶段）

1. **调研 & 架构设计** — `@architect` 接收需求，完成技术选型与 MVI 方案设计
2. **工具研发与基建沉淀** — `@infra` 准备 CLI 工具、MCP 接入、Skills 封装
3. **并行开发（TDD）** — `@tester` 先写测试，`@coder` 编写实现，`@infra` 按需提供自动化脚本
4. **Code Review** — `@coder` 提交 CR，`@tester` 审查并反馈
5. **打包** — `@coder` 调用 `@infra` 的打包脚本执行多端构建
6. **测试** — `@tester` 运行 UI 自动化测试
7. **修复与回归** — `@tester` 提 Bug，`@coder` 修复，循环至通过
8. **汇报结果** — `@tester` 确认质量门禁，`@architect` 交付成果

## 技术栈

- 架构模式：MVI
- 跨端：Compose (Android) / UIKit (iOS)
- 开发模式：TDD

## 编码规范

- 提交粒度适中，每个 commit 只解决一个问题
- 测试覆盖：正常路径、边界条件、异常场景
- 编译通过 + 无 Crash / ANR 是提交的最低标准
