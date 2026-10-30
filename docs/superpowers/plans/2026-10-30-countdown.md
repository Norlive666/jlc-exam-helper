# 无悬浮窗与延时开始实施计划

> 执行方式：在当前专用 exam-helper 分支逐项执行，保持无关文件不变，按测试先行和完成前验证流程检查。

**目标：** 将显示名改为“考试监督”，去掉悬浮窗，新增用户确认的一次倒计时并保留其他功能。

**架构：** 倒计时由 QuizAccessibilityService 管理，elapsedRealtime 截止时间加 Handler 回调负责等待；配置页只设置或取消任务，已有通知用于状态和停止。识题执行仍复用现有串行任务及权限检查。

**技术栈：** Java / Kotlin，Android 30–36，Robolectric，JUnit，GitHub Actions。

## 约束

- 保持 applicationId 与原无障碍反馈组合；显示名为“考试监督”。
- 一次倒计时由“定时开始”触发，桌面再打开不提前执行。
- 不增加唤醒、解锁、开机恢复或闹钟权限。
- 保留历史、立即重识别、通知停止、自动重试及内置测试页。
- 版本更新为 0.2.7-test / versionCode 9。

## 1. 先覆盖新行为

- [x] 在 MainActivityTest 中通过真实按钮设置倒计时，推进主线程时钟，检查 AgentSettings.isRunning 和通知，确保到期前 false、到期后 true。
- [x] 覆盖通知点击取消、重复设置、立即开始、无效输入、通知权限丢失、服务中断以及桌面重开不提前启动。
- [x] 测试应用内历史与立即重识别入口，确保删除悬浮入口后功能仍可达。
- [x] 只提交测试并由 GitHub 运行，确认因新界面/行为缺失失败。

## 2. 实施改动

- [x] QuizAccessibilityService 新增 scheduleAgent(int minutes)、isScheduled() 和 remainingStartMillis()；在 stopAgent 和 startAgent 路径取消等待。到期在当前页面执行 startAgent，不启动 Activity。
- [x] MainActivity 新增分钟输入与设置按钮；倒计时期间显示剩余状态，已有桌面启动请求不提前执行；增加历史、立即重识别、停止入口。
- [x] 删除 QuizOverlay.java、QuizOverlayTest.java，删除服务中悬浮显示、状态更新及截图隐藏延迟。
- [x] QuizTestActivity 保留测试按钮并增加历史/立即重识别入口；更新 QuizHistoryActivity 的说明，去掉悬浮窗操作提示。
- [x] 更新 strings.xml 的显示名与使用说明；版本更新为 0.2.7-test / versionCode 9。

## 3. 验证和交付

- [x] 检查差异与权限，推送到 exam-helper 分支，运行 :app:testDebugUnitTest 和 :app:assembleDebug。
- [x] 按代码审查技能进行独立审查，修复有效问题并完成对应验证。
- [x] 下载 APK 与测试报告，核对测试数、失败数、显示名、版本、反馈类型、原模块、无悬浮窗类和 SHA-256。
- [x] 更新 README 的新操作方式与构建链接，交付安装包并说明真机倒计时和后台仍需实测。

## 完成记录

- 测试先行：6f10a2c 在 GitHub 运行 66 项测试，新增 11 项因缺少输入/入口而失败，原有 55 项通过。
- 独立审查发现桌面 Intent 与倒计时到期/取消的启动竞态，17c467a 新增边界测试后复现到期跳页，595add8 在收到 Intent 时消耗已有计划的启动请求。通知测试同时按 Robolectric 的广播模拟限制修正为校验 PendingIntent 目标并调用真实接收器。
- 最终运行 41002398802 成功，8 个测试集、67 项通过。APK 已解析确认显示名、版本、原权限、反馈类型17、保留的模块及无悬浮窗类，SHA-256 与构建校验文件一致。
- 交付仍留在用户指定的 exam-helper 分支，不合并到 main。真机后台运行和实际通知操作尚未验证。
