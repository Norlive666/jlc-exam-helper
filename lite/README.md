# Operit 识题精简版

复用 Operit 原版识屏执行模块的安卓独立应用（0.2.1-test），仅接入 DeepSeek 官方 API。本临时测试版内置三题答题页，配置后进入测试页，可再转到后台识别其他应用，通过通知栏关闭识题。

识屏与点击来自原作者的 OperitAccessbility 服务；图片归一化、缩放与编码使用 Operit 的 ImagePoolManager，合并为一个 APK。具体来源和删改范围见 [UPSTREAM.md](UPSTREAM.md)。

## 当前功能

- 只需要填写一个 DeepSeek API Key，固定使用 `deepseek-flash`
- 任意应用均可识题，没有目标应用白名单
- 支持当前屏幕中完整可见的单选、多选和判断题
- 根据选项当前状态执行点击，避免把已选答案反复取消
- 手动切换题目后继续识别，不自动翻页、滑动、填写文字或提交
- 运行中不展示聊天界面、悬浮窗、边框、应用弹窗或逐次确认卡片
- 一个最低重要级别、无声音与振动的通知显示状态；点击通知本身或“关闭识题”均会停止
- 确认 API Key 和权限后，再次打开应用即开始运行并进入内置测试页；点“转到后台”继续测试其他应用
- 页面变化后废弃旧回答；关闭后取消待处理任务与请求

## 手机要求与首次使用

Android 11 / API 30 或以上，无需 Root、Shizuku 或电脑控制。

本次 0.2.1-test 与之前 0.2.0 的调试签名不同。已安装旧识题版时需要先卸载，再安装新版，并重新填写 Key、完成系统授权。

1. 安装精简版 APK，打开“Operit 识题”。它与原版 Operit 的应用 ID 不同，可以共存。
2. 输入 DeepSeek API Key，点击“保存并进入测试页”。尚未授权时会在页面内显示提示。
3. 点击“允许通知”完成系统授权；点击“开启无障碍服务”并在系统设置中启用本应用。
4. 返回应用，权限齐全后进入“临时答题测试”。保持屏幕亮起并解锁，等待自动选项，再手动核对、切换下一题。
5. 下拉通知栏，点击“关闭识题”。重新打开应用可再次开始。

Android 的无障碍授权、通知授权以及部分手机的后台管理提示属于系统界面，首次使用需要本人完成。通知是否显示状态栏图标也由手机系统和用户的通知设置决定。

## 内置测试页

页面使用真实单选按钮和复选框，依次显示：

| 题型 | 题目 | 正确选项 |
|---|---|---|
| 单选 | 2 + 3 = ? | B（5） |
| 多选 | 以下哪些数是素数？ | A、B、D（2、3、5） |
| 判断 | 1 千米等于 1000 米 | A（正确） |

等待自动选中后，点“核对本题”查看结果；“重做本题”清空选择，“下一题”循环切换三题。测试页不会按标准答案自动填写，仍需真实截图、DeepSeek 回答与手势点击。

点“配置”修改 Key 或权限；点“转到后台”测试其他应用；通知栏关闭后，可在测试页点“开始识题”重新运行。页面状态、核对结果和题目更换会触发新的识别，因此应等待自动选择后再核对。

临时例外只允许已恢复且持有焦点的测试页被读取，设置页继续禁止识屏。测试完成后，按 [TEST-PLAN.md](TEST-PLAN.md) 移除此页面和少量入口，不改识题模块。

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

0.2.1-test 新增 5 项 Robolectric 答题控件与生命周期回归测试，验证单选、多选、判断、重做/换题、测试页焦点和设置页保护。

2026-10-24，[GitHub Actions 构建](https://github.com/Norlive666/jlc-exam-helper/actions/runs/40095294988) 已验证提交 `dfdb85e`：**28 项测试全部通过**。APK 为 **2,603,576 字节**，已下载核对完整性、测试页与 Operit 模块、版本号和校验值。

[下载 0.2.1-test APK 产物](https://github.com/Norlive666/jlc-exam-helper/actions/runs/40095294988/artifacts/12560986040)，解压后安装 APK。

SHA-256：`aa7e324652eaa6ff648e3d1fa072993ed3d7ac4ecc327c7823d5457141e34002`。

真实 API 与手机自动识题点击仍待用户实测。

许可证与改写来源见 [UPSTREAM.md](UPSTREAM.md)。
