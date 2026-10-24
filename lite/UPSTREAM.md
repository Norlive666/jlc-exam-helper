# 来源与许可

本精简版派生自 [Operit](https://github.com/AAswordman/Operit)，基线提交为 `dbf71916fae9750cfdc9f9a774f5a0fee56633fb`，发布于 `Norlive666/jlc-exam-helper` 的 `exam-helper` 分支。

轻量构建只包含 `lite/app/`，没有引入原版的聊天、终端或本地模型模块。原版源码可通过上游仓库与基线提交追溯。

选项坐标映射沿用并改写自上游 [PhoneAgent.kt](https://github.com/AAswordman/Operit/blob/dbf71916fae9750cfdc9f9a774f5a0fee56633fb/app/src/main/java/com/ai/assistance/operit/core/tools/agent/PhoneAgent.kt) 中的 `parseRelativePoint`：模型使用 0–1000 的相对坐标，由执行端映射到原始屏幕。屏幕读取和手势直接使用 Android 无障碍接口，在精简包中合并为一个服务，省去原版的外部 Provider 和通用工具分发层。精简版设置界面、题目协议、会话和通知生命周期为此次新增代码，并非完整复用原版 PhoneAgent。

本衍生目录遵循随附的 [GNU LGPL v3 许可](LICENSE)。原 Operit 项目的权利与署名归原作者所有。Gradle Wrapper 保留其文件中的 Apache 2.0 版权说明。分发时应同时提供相应源码与许可证。
