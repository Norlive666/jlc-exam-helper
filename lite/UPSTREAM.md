# 来源、删改范围与许可

0.2.0 修正了 0.1.0 仅沿用坐标规则、未复用原版识屏执行模块的问题。当前 `lite/app` 依赖 `lite/operit-core`，运行时使用下列原项目代码。

## Operit 主仓库

来源：[AAswordman/Operit](https://github.com/AAswordman/Operit)，固定基线 `dbf71916fae9750cfdc9f9a774f5a0fee56633fb`。

- `operit-core/src/main/java/com/ai/assistance/operit/util/ImagePoolManager.kt`：从基线同名文件完整复制，内容未修改。每帧经 `OperitScreenshot.encode` 调用原版图片池完成方向、缩放、透明通道和 JPEG 编码，发送后移除图片。
- `OperitPaths.kt`：从同名文件仅保留图片池路径与目录创建代码；未引入公共下载目录、插件、工作区等路径。
- `AppLogger.kt`：新的 Android 日志适配器，替代依赖整个应用的文件日志实现。
- 坐标映射继续沿用并改写自原版 `core/tools/agent/PhoneAgent.kt` 的 `parseRelativePoint`，使用 0–1000 相对坐标映射原屏幕尺寸。

主仓库衍生代码遵循随附 [GNU LGPL v3](LICENSE)。原项目权利与署名归原作者所有。

## Operit 无障碍组件

来源：[AAswordman/OperitAccessbility](https://github.com/AAswordman/OperitAccessbility)，固定基线 `1221cf08c40f48ee5dfa68d3fc2c5aacbbf01b6a`。

`operit-core/src/main/java/com/ai/assistance/operit/provider/UIAccessibilityService.kt` 从原版同名服务删减而来：

- 保留 `performClick` 的手势构造、50 ms 点击与完成/取消回调。
- 保留截图的 1100 ms 间隔、系统截图回调、HardwareBuffer 和文件编码流程。
- 保留 `captureUiHierarchyAsXml`、递归 XML 序列化和窗口事件处理。
- 删除输入文字、长按、滑动、全局按键、查找焦点及外部 Binder/AIDL；直接在同一 APK 中由 `QuizAccessibilityService` 继承使用，无须再装 Provider APK。
- 适配改动：类允许继承；方法改为本地调用；点击完成状态回传；截图增加超时、停止取消及迟到回调清理；XML 增补选中状态字段。

上游 [README](operit-core/PROVIDER-README.upstream.md) 声明 Apache 2.0，随附 [Apache 2.0 许可](operit-core/LICENSE-APACHE-2.0)。保留原作者署名与上述修改说明。

## 本次新增与未引入部分

设置、DeepSeek 接口、题目协议、专用会话、屏幕变化判断及通知为本项目新增代码。没有引入完整 PhoneAgent 的通用动作循环、聊天、终端、本地模型、Shizuku 或悬浮界面，不能将当前版本称为完整 Operit。

Android 安装包内附上述许可证。Gradle Wrapper 保留其 Apache 2.0 文件声明。根目录的原油猴脚本继续使用 MIT 许可。对应源码在 `Norlive666/jlc-exam-helper` 的 `exam-helper` 分支公开。
