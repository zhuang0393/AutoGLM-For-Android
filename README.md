# AutoGLM For Android Source Code

<div align="center">
<img src="screenshots/logo.svg" width="120"/>

**基于 Open-AutoGLM 的 Android 原生手机智能助手应用**

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

中文 | [English](README_en.md)

</div>

##  关于此 Fork 版本

本项目 Fork 自 [AutoGLM For Android](https://github.com/Luokavin/AutoGLM-For-Android)，感谢原作者 [Luokavin](https://github.com/Luokavin) 的杰出工作。

**主要改动说明：**

1.  **移除 Shizuku 依赖**：本项目不再依赖 Shizuku 获取权限，而是直接运行在 Android 系统内部。
2.  **基于系统源码编译**：本项目改造为在 Android 系统源码环境下编译（Android.bp），作为系统应用（priv-app）集成。
3.  **系统级权限**：利用系统签名和 `Instrumentation` API 直接执行模拟点击、截图等操作，权限更稳定，体验更流畅。
4.  **无障碍服务集成**：新增 AccessibilityService 支持，通过 Android 标准无障碍 API 实现可靠的截图和 UI 交互，完美适配 Android Automotive (HSUM) 等复杂环境。
