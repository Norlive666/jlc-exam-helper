# JLC Exam Helper

考试星 / 嘉立创考试页面的 DeepSeek 图文题填写助手，当前版本 v1.2.1。

[安装油猴脚本](https://raw.githubusercontent.com/Norlive666/jlc-exam-helper/main/kaoshixing-deepseek.user.js) · [源码](https://github.com/Norlive666/jlc-exam-helper/blob/main/kaoshixing-deepseek.user.js)

先安装 Tampermonkey，再打开安装链接；如果浏览器显示源码，可将内容复制到篡改猴新建脚本中。脚本已声明 GitHub 自动更新地址，实际检查周期由篡改猴设置决定。

## 安装与更新

1. Edge / Firefox 安装官方扩展商店中的 Tampermonkey（篡改猴）。
2. 新建脚本，或在原脚本中全选替换为 `kaoshixing-deepseek.user.js`，Ctrl+S 保存。更新时保留原脚本条目，不要重复安装多份。
3. 确认网站已保存现有答案，再刷新考试页面。支持考试星直接页面及内嵌考试星的嘉立创入口。
4. 没有已保存的 API Key 时，脚本会自动调用浏览器输入弹窗。粘贴并确认后，Key 长期保存在篡改猴脚本存储中。弹窗显示明文，勿截图或共享屏幕。
5. 有保存的 Key 时直接复用，不重复弹窗。篡改猴菜单“设置 DeepSeek API Key（长期保存）”可修改；输入空白并确认可删除，取消保留原值。
6. 面板模型使用 `deepseek-flash`，点击“开始当前页”。处理记录显示题库或 AI 来源；完成后自行核对答案和网站保存状态，再手动交卷。

如果浏览器阻止自动弹窗（跨域 iframe 中尤其可能），脚本不能保证强制弹出；请使用篡改猴菜单入口。网页里不再显示 Key 输入框。

## 本地题库

- 每题先查篡改猴存储中的题库。题干文字、题型、全部选项顺序及文字、图片 URL 必须完全一致才命中。
- 命中后直接使用缓存答案，不请求 API。选项顺序或图片链接变化会重新调用 AI，避免错误复用选项字母。
- 未命中时请求 DeepSeek。仅将格式及选项范围校验通过的 AI 答案存入题库；AI 报错、无效答案、停止后的响应不入库。
- 缓存是 AI 的历史回答，未经人工核验，不等于标准答案。即使错误也可能被复用；本版不自动识别网站判分结果。
- Key 和题库按浏览器配置中的这个脚本保存，刷新、关闭浏览器后仍可使用。Edge 与 Firefox 不自动共享；卸载脚本并删除脚本数据、清理扩展数据会丢失。
- 题库只存在本地脚本存储；未命中的题干、选项和图片链接会发送给 DeepSeek，并产生 API 费用。Key 不写入网页 DOM 或日志；扩展存储不是专用加密保险箱。

## 支持范围

只处理开始时已加载并显示的 `.exam-question`，不自动翻页、不交卷。
支持标准 checkbox 多选、radio 单选；判断题需同样的 input/label 结构。已有答案跳过。
题干及选项的标准 img 图片以考试星 HTTPS 图片链接发送给 DeepSeek。图片需要登录或已过期时可能无法读取；SVG、画布、背景图及音视频仍跳过。
“停止”会中止当前 API 请求，之后的响应不再填写或缓存；此前已填写、已缓存的题目会保留。

图像接口参考：https://api-docs.deepseek.com/guides/vision/

## 验证

```bash
node answers.test.cjs
node images.test.cjs
node key-menu.test.cjs
node bank.test.cjs
node --check kaoshixing-deepseek.user.js
```

单元测试使用模拟的扩展存储。真实考试网页、真实 DeepSeek 请求及浏览器重启持久化仍需安装后验证。
本仓库的测试不依赖浏览器或真实 API，可使用 Node.js 运行 `npm test`。
