# Operit 识题精简版

复用 Operit 原版识屏执行模块的安卓独立应用（0.2.0），仅接入 DeepSeek 官方 API。打开后后台读取当前屏幕，识别可见的选择题、判断题并选择答案，通过通知栏关闭。

识屏与点击来自原作者的 OperitAccessbility 服务；图片归一化、缩放与编码使用 Operit 的 ImagePoolManager，合并为一个 APK。具体来源和删改范围见 [UPSTREAM.md](UPSTREAM.md)。

## 当前功能

- 只需要填写一个 DeepSeek API Key，固定使用 `deepseek-flash`
- 任意应用均可识题，没有目标应用白名单
- 支持当前屏幕中完整可见的单选、多选和判断题
- 根据选项当前状态执行点击，避免把已选答案反复取消
- 手动切换题目后继续识别，不自动翻页、滑动、填写文字或提交
- 运行中不展示聊天界面、悬浮窗、边框、应用弹窗或逐次确认卡片
- 一个最低重要级别、无声音与振动的通知显示状态；点击通知本身或“关闭识题”均会停止
- 确认 API Key 和权限后，再次打开应用即开始运行并返回后台
- 页面变化后废弃旧回答；关闭后取消待处理任务与请求

## 手机要求与首次使用

Android 11 / API 30 或以上，无需 Root、Shizuku 或电脑控制。

1. 安装精简版 APK，打开“Operit 识题”。它与原版 Operit 的应用 ID 不同，可以共存。
2. 输入 DeepSeek API Key，点击“保存并后台运行”。尚未授权时会在页面内显示提示。
3. 点击“允许通知”完成系统授权；点击“开启无障碍服务”并在系统设置中启用本应用。
4. 返回应用，权限齐全后自动转入后台。切换到需要识别的题目页面，保持屏幕亮起并解锁。
5. 下拉通知栏，点击“关闭识题”。重新打开应用可再次开始。

Android 的无障碍授权、通知授权以及部分手机的后台管理提示属于系统界面，首次使用需要本人完成。通知是否显示状态栏图标也由手机系统和用户的通知设置决定。

## 数据与使用范围

运行时，当前屏幕的 JPEG 截图和可读取的界面文字由手机直接发送至 `https://api.deepseek.com/chat/completions`。API Key 使用 Android Keystore 加密保存。截图经 Operit 原版截图流程短暂写入应用私有缓存，处理后删除，不写入相册。图片池不启用磁盘持久化。

屏幕每轮间隔约三秒检查一次，模型请求和点击串行执行。相同界面会跳过重复请求。受保护的页面可能无法截图；模型定位和答题也可能出错，当前版本尚无真机成功率数据。

## 构建入口

使用 JDK 17 或 21、Android SDK Platform 36，并配置 `ANDROID_HOME` 或 `lite/local.properties` 中的 `sdk.dir`。无需下载原版的 native 模块、终端子模块或模型资源。

在仓库根目录执行：

```sh
./lite/gradlew -p lite :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1
```

APK 路径：`lite/app/build/outputs/apk/debug/app-debug.apk`。

## GitHub 构建与下载

推送到 `exam-helper` 分支后，[Build Exam Helper APK](https://github.com/Norlive666/jlc-exam-helper/actions/workflows/exam-helper-android.yml) 会运行 JVM 测试并构建 Debug APK，也可以在 Actions 页面选择该分支手动运行。

成功后，打开该次运行页面，在 Artifacts 中下载 `exam-helper-debug-apk`。解压得到可安装的 `exam-helper-debug.apk` 和 SHA-256 校验文件。下载 Actions 产物通常需要登录 GitHub。

## 验证状态

已提供 21 个 JVM 行为回归场景，覆盖请求格式、点击坐标、多选状态、重复题目、过期回答、同屏多题与手动改变选择、自动换题、停止后不点击，以及原版 XML 的选中状态归一化。另有 2 个 Robolectric 原生位图场景，验证真实 JPEG 编码、缩放与每帧图片清理。

0.2.0 的 GitHub 构建结果待确认。CI 同时检查 APK 中包含实际编译的 UIAccessibilityService、ImagePoolManager 与 OperitScreenshot；真实 API 请求与手机运行尚未验证。

许可证与改写来源见 [UPSTREAM.md](UPSTREAM.md)。
