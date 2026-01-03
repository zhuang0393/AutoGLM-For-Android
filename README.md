# AutoGLM For Android

<div align="center">
<img src="screenshots/logo.svg" width="120"/>

**基于 Open-AutoGLM 的 Android 原生手机智能助手应用**

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

中文 | [English](README_en.md)

</div>

## 📸 应用截图

<table>
  <tr>
    <td><img src="screenshots/screenshot_1.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_2.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_3.jpg" width="100%"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/screenshot_4.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_5.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_6.jpg" width="100%"/></td>
  </tr>
</table>

---

## 📖 项目简介

AutoGLM For Android 是基于 [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) 开源项目二次开发的 Android 原生应用。它将原本需要电脑 + ADB 连接的手机自动化方案，转变为一个独立运行在手机上的 App，让用户可以直接在手机上使用自然语言控制手机完成各种任务。

**核心特点：**

- 🚀 **无需电脑**：直接在手机上运行，无需 ADB 连接
- 🎯 **自然语言控制**：用自然语言描述任务，AI 自动执行
- 🔒 **Shizuku 权限**：通过 Shizuku 获取必要的系统权限
- 🪟 **悬浮窗交互**：悬浮窗实时显示任务执行进度
- 📱 **原生体验**：Material Design 设计，流畅的原生 Android 体验
- 🔌 **多模型支持**：兼容任何支持 OpenAI 格式和图片理解的模型 API

## 🏗️ 架构对比

| 特性     | Open-AutoGLM (原版) | AutoGLM For Android (本项目) |
| -------- | ------------------- | ---------------------------- |
| 运行环境 | 电脑 (Python)       | 手机 (Android App)           |
| 设备连接 | 需要 ADB/USB 连接   | 无需连接，独立运行           |
| 权限获取 | ADB shell 命令      | Shizuku 服务                 |
| 文本输入 | ADB Keyboard        | 内置 AutoGLM Keyboard        |
| 用户界面 | 命令行              | 原生 Android UI + 悬浮窗     |
| 截图方式 | ADB screencap       | Shizuku shell 命令           |

## 📋 功能特性

### 核心功能

- ✅ **任务执行**：输入自然语言任务描述，AI 自动规划并执行
- ✅ **屏幕理解**：截图 → 视觉模型分析 → 输出操作指令
- ✅ **多种操作**：点击、滑动、长按、双击、输入文本、启动应用等
- ✅ **任务控制**：暂停、继续、取消任务执行
- ✅ **历史记录**：保存任务执行历史，支持查看详情和截图

### 用户界面

- ✅ **主界面**：任务输入、状态显示、快捷操作
- ✅ **悬浮窗**：实时显示执行步骤、思考过程、操作结果
- ✅ **设置页面**：模型配置、Agent 参数、多配置管理
- ✅ **历史页面**：任务历史列表、详情查看、截图标注

### 高级功能

- ✅ **多模型配置**：支持保存多个模型配置，快速切换
- ✅ **任务模板**：保存常用任务，一键执行
- ✅ **自定义 Prompt**：支持自定义系统提示词
- ✅ **快捷磁贴**：通知栏快捷磁贴，快速打开悬浮窗
- ✅ **日志导出**：支持导出调试日志，自动脱敏敏感信息

## 📱 系统要求

- **Android 版本**：Android 7.0 (API 24) 及以上
- **必需应用**：[Shizuku](https://shizuku.rikka.app/) (用于获取系统权限)
- **网络连接**：需要连接到模型 API 服务（支持任何 OpenAI 格式兼容的视觉模型）
- **权限要求**：
  - 悬浮窗权限 (用于显示悬浮窗)
  - 网络权限 (用于 API 通信)
  - Shizuku 权限 (用于执行系统操作)

## 🚀 快速开始

### 第一步：安装并激活 Shizuku

Shizuku 是本应用的核心依赖，用于执行屏幕点击、滑动等操作。

**下载安装**

- [Google Play 下载](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
- [GitHub 下载](https://github.com/RikkaApps/Shizuku/releases)

**激活方式（三选一）**

| 方式      | 适用场景       | 持久性           |
| --------- | -------------- | ---------------- |
| 无线调试  | 推荐，无需电脑 | 重启后需重新配对 |
| ADB 连接  | 有电脑时使用   | 重启后需重新执行 |
| Root 授权 | 已 Root 设备   | 永久有效         |

**无线调试激活步骤（推荐）**

1. 连接任意 WIFI
2. 打开手机「设置」→「开发者选项」
3. 开启「无线调试」
4. 点击「使用配对码配对设备」
5. 等待 Shizuku 通知弹出，在通知内输入配对码完成配对
6. 打开 Shizuku 点击「启动」，等待启动完毕
7. 看到 Shizuku 显示「正在运行」即为成功

<table>
  <tr>
    <td><img src="screenshots/screenshot_7.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_8.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_9.jpg" width="100%"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/screenshot_10.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_11.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_12.jpg" width="100%"/></td>
  </tr>
</table>

> 💡 **提示**：如果找不到开发者选项，请在「关于手机」中连续点击「版本号」多次开启。

### 第二步：安装 AutoGLM For Android

1. 从 [Releases 页面](https://github.com/Luokavin/AutoGLM-For-Android/releases) 下载最新 APK
2. 安装 APK 并打开应用

### 第三步：授予必要权限

打开应用后，需要依次授予以下权限：

| 权限         | 用途             | 操作                                    |
| ------------ | ---------------- | --------------------------------------- |
| Shizuku 权限 | 执行屏幕操作     | 点击「授权」→ 始终允许                  |
| 悬浮窗权限   | 显示任务执行窗口 | 点击「授权」→ 开启开关                  |
| 键盘权限     | 输入文本内容     | 点击「启用键盘」→ 启用 AutoGLM Keyboard |

<table>
  <tr>
    <td><img src="screenshots/screenshot_13.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_14.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_15.jpg" width="100%"/></td>
  </tr>
</table>

> 💡 **提示**：如果悬浮窗无法授权，进入应用详情页，点击「右上角菜单」→ 允许受限制的设置，再次尝试授权悬浮窗。

### 第四步：配置模型服务

进入「设置」页面，配置 AI 模型 API：

**推荐配置（智谱 BigModel）** 🎉 目前 `autoglm-phone` 模型限时免费！

| 配置项   | 值                                                                                |
| -------- | --------------------------------------------------------------------------------- |
| Base URL | `https://open.bigmodel.cn/api/paas/v4`                                            |
| Model    | `autoglm-phone`                                                                   |
| API Key  | 在 [智谱 AI 开放平台](https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys) 获取 |

**备选配置（ModelScope）**

| 配置项   | 值                                           |
| -------- | -------------------------------------------- |
| Base URL | `https://api-inference.modelscope.cn/v1`     |
| Model    | `ZhipuAI/AutoGLM-Phone-9B`                   |
| API Key  | 在 [ModelScope](https://modelscope.cn/) 获取 |

配置完成后，点击「测试连接」验证配置是否正确。

<table>
  <tr>
    <td><img src="screenshots/screenshot_16.png" width="100%"/></td>
  </tr>
</table>

**使用其他第三方模型**：

只要模型服务满足以下条件，即可在本应用中使用：

1. **API 格式兼容**：提供 OpenAI 兼容的 `/chat/completions` 端点
2. **多模态支持**：支持 `image_url` 格式的图片输入
3. **图片理解能力**：能够分析屏幕截图并理解 UI 元素

> ⚠️ **注意**：非 AutoGLM 模型可能需要调整系统提示词才能正确输出操作指令格式。可在设置 → 高级设置中自定义系统提示词。

### 第五步：开始使用

1. 在主界面输入任务描述，如："打开微信，给文件传输助手发送消息：测试"
2. 点击「开始任务」按钮
3. 悬浮窗会自动弹出，显示执行进度
4. 观察 AI 的思考过程和执行操作

---

## 📖 使用教程

### 基本操作

**启动任务**：

1. 在主界面或悬浮窗输入任务描述
2. 点击「开始」按钮
3. 应用会自动截图、分析、执行操作

**控制任务**：

| 按钮    | 功能                 |
| ------- | -------------------- |
| ⏸️ 暂停 | 在当前步骤后暂停执行 |
| ▶️ 继续 | 恢复暂停的任务       |
| ⏹️ 停止 | 取消当前任务         |

**查看历史**：

1. 点击主界面右上角的「历史」图标
2. 查看所有执行过的任务列表
3. 点击任务可查看详细步骤和截图

### 任务示例大全

**社交通讯**

```
打开微信，搜索张三并发送消息：明天有空吗？
打开微信，查看朋友圈最新动态
```

**购物搜索**

```
打开淘宝，搜索无线耳机，按销量排序
打开京东，搜索手机壳，筛选价格50元以下
```

**外卖点餐**

```
打开美团，搜索附近的火锅店
打开饿了么，点一份黄焖鸡米饭
```

**出行导航**

```
打开高德地图，导航到最近的地铁站
打开百度地图，搜索附近的加油站
```

**视频娱乐**

```
打开抖音，刷5个视频
打开B站，搜索编程教程
```

### 高级功能

**保存模型配置**：

如果你有多个模型 API，可以保存为不同配置：

1. 进入「设置」→「模型配置」
2. 配置好参数后点击「保存配置」
3. 输入配置名称（如：智谱、OpenAI）
4. 之后可在配置列表中快速切换

**创建任务模板**：

将常用任务保存为模板，一键执行：

1. 进入「设置」→「任务模板」
2. 点击「添加模板」
3. 输入模板名称和任务描述
4. 在主界面点击模板按钮快速选择

**自定义系统提示词**：

针对特定场景优化 AI 表现：

1. 进入「设置」→「高级设置」
2. 编辑系统提示词
3. 添加特定领域的指令增强

**快捷磁贴**：

在通知栏添加快捷磁贴，快速打开悬浮窗：

1. 下拉通知栏，点击编辑图标
2. 找到「AutoGLM」磁贴
3. 拖动到快捷磁贴区域

**导出调试日志**：

遇到问题时，可导出日志用于排查：

1. 进入「设置」→「关于」
2. 点击「导出日志」
3. 日志会自动脱敏敏感信息

### 使用技巧

1. **任务描述要清晰**：尽量具体描述你想要完成的操作
2. **分步执行复杂任务**：复杂任务可以拆分成多个简单任务
3. **善用暂停功能**：在关键步骤前暂停，确认后再继续
4. **保存常用模板**：将重复性任务保存为模板提高效率
5. **定期检查 Shizuku**：确保 Shizuku 服务持续运行

## 🛠️ 开发教程

### 环境准备

**开发工具**：

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 11 或更高版本
- Kotlin 1.9.x

**克隆项目**：

```bash
git clone https://github.com/your-repo/AutoGLM-For-Android.git
cd AutoGLM-For-Android
```

**打开项目**：

1. 启动 Android Studio
2. 选择 "Open an existing project"
3. 选择项目根目录
4. 等待 Gradle 同步完成

### 项目结构

```
app/src/main/java/com/kevinluo/autoglm/
├── action/                 # 动作处理模块
│   ├── ActionHandler.kt    # 动作执行器
│   ├── ActionParser.kt     # 动作解析器
│   └── AgentAction.kt      # 动作数据类
├── agent/                  # Agent 核心模块
│   ├── PhoneAgent.kt       # 手机 Agent 主类
│   └── AgentContext.kt     # 对话上下文管理
├── app/                    # 应用基础模块
│   ├── AppInfo.kt          # 应用信息数据类
│   ├── AppResolver.kt      # 应用名称解析
│   └── AutoGLMApplication.kt
├── config/                 # 配置模块
│   ├── I18n.kt             # 国际化
│   └── SystemPrompts.kt    # 系统提示词
├── device/                 # 设备操作模块
│   └── DeviceExecutor.kt   # 设备命令执行
├── history/                # 历史记录模块
│   ├── HistoryManager.kt   # 历史管理
│   ├── HistoryActivity.kt  # 历史界面
│   ├── HistoryDetailActivity.kt  # 历史详情界面
│   ├── HistoryDetailAdapter.kt   # 历史详情适配器
│   ├── HistoryModels.kt    # 历史数据模型
│   └── ScreenshotAnnotator.kt    # 截图标注工具
├── input/                  # 输入模块
│   ├── TextInputManager.kt # 文本输入管理
│   ├── KeyboardHelper.kt   # 键盘辅助工具
│   └── AutoGLMKeyboardService.kt  # 内置键盘
├── model/                  # 模型通信模块
│   └── ModelClient.kt      # API 客户端
├── screenshot/             # 截图模块
│   └── ScreenshotService.kt # 截图服务
├── settings/               # 设置模块
│   ├── SettingsManager.kt  # 设置管理
│   └── SettingsActivity.kt # 设置界面
├── ui/                     # UI 模块
│   ├── FloatingWindowService.kt  # 悬浮窗服务
│   ├── FloatingWindowTileService.kt  # 快捷磁贴服务
│   ├── FloatingWindowToggleActivity.kt  # 悬浮窗切换
│   └── MainViewModel.kt    # 主界面 ViewModel
├── util/                   # 工具模块
│   ├── CoordinateConverter.kt    # 坐标转换
│   ├── ErrorHandler.kt     # 错误处理
│   ├── HumanizedSwipeGenerator.kt # 人性化滑动
│   ├── LogFileManager.kt   # 日志文件管理与导出
│   └── Logger.kt           # 日志工具
├── ComponentManager.kt     # 组件管理器
├── MainActivity.kt         # 主界面
└── UserService.kt          # Shizuku 用户服务
```

### 核心模块说明

**PhoneAgent (agent/PhoneAgent.kt)**

- 核心 Agent 类，负责任务执行流程
- 管理截图 → 模型请求 → 动作执行的循环
- 支持暂停、继续、取消操作

**ModelClient (model/ModelClient.kt)**

- 与模型 API 通信
- 支持 SSE 流式响应
- 解析思考过程和动作指令

**ActionHandler (action/ActionHandler.kt)**

- 执行各种设备操作
- 协调 DeviceExecutor、TextInputManager 等组件
- 管理悬浮窗显示/隐藏

**DeviceExecutor (device/DeviceExecutor.kt)**

- 通过 Shizuku 执行 shell 命令
- 实现点击、滑动、按键等操作
- 支持人性化滑动轨迹

**ScreenshotService (screenshot/ScreenshotService.kt)**

- 截取屏幕并压缩为 WebP
- 自动隐藏悬浮窗避免干扰
- 支持敏感页面检测

### 构建和调试

**Debug 构建**：

```bash
./gradlew assembleDebug
```

**Release 构建**：

```bash
./gradlew assembleRelease
```

**运行测试**：

```bash
./gradlew test
```

**安装到设备**：

```bash
./gradlew installDebug
```

## 🔧 常见问题

### Shizuku 相关

**Q: Shizuku 显示未运行？**

A: 确保 Shizuku 已安装并打开，按指引激活服务。推荐使用无线调试方式。

**Q: 每次重启后 Shizuku 失效？**

A: 无线调试方式需要重新配对。可考虑：

- Root 方式永久激活
- 使用 ADB 方式激活

### 权限相关

**Q: 悬浮窗权限无法授予？**

A: 手动操作：系统设置 → 应用 → AutoGLM → 权限 → 开启「显示在其他应用上层」

**Q: 键盘无法启用？**

A: 手动操作：系统设置 → 语言和输入法 → 管理键盘 → 启用 AutoGLM Keyboard

### 操作相关

**Q: 点击操作不生效？**

A:

1. 检查 Shizuku 是否正在运行
2. 部分系统需开启「USB 调试(安全设置)」
3. 尝试重启 Shizuku

**Q: 文本输入失败？**

A:

1. 确保 AutoGLM Keyboard 已启用
2. 尝试手动切换一次输入法后再执行任务

**Q: 截图显示黑屏？**

A: 这是敏感页面（支付、密码等）的正常保护机制，应用会自动检测并标记。

### 模型相关

**Q: API 连接失败？**

A:

1. 检查网络连接
2. 确认 API Key 是否正确
3. 确认 Base URL 格式正确（末尾不要加 `/`）

**Q: 模型响应很慢？**

A:

1. 检查网络质量
2. 尝试切换其他模型服务
3. 在设置中调整超时时间

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Luokavin/AutoGLM-For-Android&type=Date)](https://star-history.com/#Luokavin/AutoGLM-For-Android&Date)

## 🙏 致谢

- [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) - 原始开源项目
- [Shizuku](https://github.com/RikkaApps/Shizuku) - 系统权限框架
- [智谱 AI](https://www.zhipuai.cn/) - AutoGLM 模型提供方

## 📞 联系方式

- Issues: [GitHub Issues](https://github.com/your-repo/issues)
- Email: luokavin@foxmail.com

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐ Star！**

</div>
