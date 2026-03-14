---
name: 设备验证流程
description: 任务完成后必须构建APK安装到设备验证，通过后才能提交代码
type: feedback
---

任务完成后必须走设备验证流程：构建 APK → 安装到设备 → 启动 App → 验证功能 → 确认通过后才提交代码。

**Why:** 用户明确要求 "任务完成后自动安装apk，打开app，验证功能通过后再提交代码"。

**How to apply:**
1. 代码改动完成 + 单元测试通过后，执行 `bash gradlew :sample:assembleDebug`
2. `adb install -r sample/build/outputs/apk/debug/sample-debug.apk`
3. `adb shell am start -n com.aspect.compose.inspector.sample/.MainActivity`
4. 通过 adb 操作 + 截屏验证功能正确
5. 确认无 Crash、功能正常后才 `git commit`

**设备交互注意事项：**
- FAB 坐标：密度3x下约 (936, 2151)
- Overlay X 关闭按钮：约 (972, 1014)
- TYPE_APPLICATION_PANEL 窗口在此设备上可通过 adb input tap 触达
- Overlay 会遮挡 App 按钮，需先关闭 overlay 再操作 App
