# AutoGLM For Android Source Code

<div align="center">
<img src="screenshots/logo.svg" width="120"/>

**Native Android Phone AI Assistant Based on Open-AutoGLM**

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

English | [中文](README.md)

</div>

## 📢 About This Fork

This project is forked from [AutoGLM For Android](https://github.com/Luokavin/AutoGLM-For-Android). Special thanks to the original author [Luokavin](https://github.com/Luokavin) for their outstanding work.

**Key Changes:**

1.  **Removed Shizuku Dependency**: This version does not rely on Shizuku for permissions but runs directly within the Android system context.
2.  **System Source Build**: Refactored to build within the Android system source tree (Android.bp) and integrated as a system application (priv-app).
3.  **System-Level Permissions**: Utilizes platform signature and `Instrumentation` API to directly execute simulated touches, screenshots, etc., offering more stability and smoother experience.
4.  **Accessibility Service Integration**: Added AccessibilityService support to reliably capture screenshots and interact with UI via standard APIs, perfectly adapting to complex environments like Android Automotive (HSUM).
