# Android.bp 系统构建中集成 Sherpa-ONNX 的方案

## 问题

在 Android.bp 系统构建中，不能直接使用 Gradle 依赖（如 JitPack），需要将 sherpa-onnx 库集成到系统源码中。

## 解决方案

### 方案 1：在系统源码中编译 sherpa-onnx（推荐）

如果您的系统源码树支持，可以在系统中编译 sherpa-onnx 库：

1. **将 sherpa-onnx 添加到系统源码**

```bash
# 在系统源码的合适位置（如 vendor/semidrive/libs/）添加 sherpa-onnx
cd vendor/semidrive/libs/
git clone https://github.com/k2-fsa/sherpa-onnx.git
```

2. **创建 Android.bp 文件编译 sherpa-onnx**

在 `vendor/semidrive/libs/sherpa-onnx/` 创建 `Android.bp`：

```bp
// 编译 sherpa-onnx JNI 库
cc_library_shared {
    name: "libsherpa-onnx",
    srcs: [
        // 添加 sherpa-onnx 的 C++ 源文件
    ],
    shared_libs: [
        "libonnxruntime",
    ],
    cflags: [
        "-std=c++17",
    ],
    header_libs: [
        "sherpa-onnx-headers",
    ],
}

// Java 包装库
java_library {
    name: "sherpa-onnx",
    srcs: [
        "java/**/*.java",
    ],
    libs: [
        "libsherpa-onnx",
    ],
    sdk_version: "current",
}
```

3. **在 AutoGLM 的 Android.bp 中添加依赖**

```bp
android_app {
    name: "AutoGLM",
    // ... 其他配置

    libs: [
        "sherpa-onnx",  // 添加 sherpa-onnx 库
    ],
}
```

### 方案 2：使用预编译的 AAR 库（简单但需要手动管理）

1. **下载预编译的 AAR**

从 JitPack 或其他来源下载 sherpa-onnx 的 AAR 文件。

2. **创建 Android.bp 使用预编译库**

```bp
// 在项目根目录或 libs/ 目录下
android_library_import {
    name: "sherpa-onnx-prebuilt",
    aars: ["libs/sherpa-onnx-1.12.20.aar"],
    sdk_version: "current",
}

android_app {
    name: "AutoGLM",
    // ... 其他配置

    libs: [
        "sherpa-onnx-prebuilt",
    ],
}
```

### 方案 3：使用 Android 原生 SpeechRecognizer（备选方案）

如果无法集成 sherpa-onnx，可以使用 Android 系统内置的 `SpeechRecognizer` API：

**优点：**
- 无需额外依赖
- 系统级支持
- 自动处理网络连接

**缺点：**
- 需要网络连接（在线识别）
- 依赖 Google 服务
- 部分设备可能不支持离线识别

**实现：**

创建一个新的 `AndroidSpeechRecognizer.kt` 作为备选实现，在 `VoiceInputManager` 中根据库可用性选择使用哪个识别器。

### 方案 4：条件编译（当前实现）

当前代码已经实现了条件编译：

1. **运行时检测库是否可用**
   - 使用 `Class.forName()` 检测 sherpa-onnx 类是否存在
   - 如果不存在，使用 stub 实现

2. **编译时兼容**
   - 使用 `typealias` 定义 stub 类型，允许在没有库的情况下编译通过
   - 实际实现代码被注释，需要手动取消注释

3. **使用方式**
   - **Gradle 构建**：取消注释 import 和实现代码，库通过依赖提供
   - **系统构建**：保持 stub 实现，或集成预编译库后取消注释

## 当前代码状态

代码已经支持条件编译：
- ✅ 使用 `SHERPA_ONNX_AVAILABLE` 运行时检测
- ✅ 使用 stub 类型定义允许编译通过
- ✅ 实现代码被注释，需要根据构建方式选择

## 推荐操作

### 对于 Android.bp 系统构建：

1. **如果系统中有 sherpa-onnx 库**：
   - 在 `Android.bp` 的 `libs` 中添加库依赖
   - 取消注释 `SherpaOnnxRecognizer.kt` 中的 import 和实现代码

2. **如果系统中没有 sherpa-onnx 库**：
   - 保持当前 stub 实现
   - 或实现 Android 原生 SpeechRecognizer 作为备选
   - 或按照方案 1/2 集成库

3. **混合方案**：
   - 优先尝试使用 sherpa-onnx
   - 如果不可用，自动降级到 Android 原生 SpeechRecognizer

## 下一步

1. 检查您的系统源码中是否已有 sherpa-onnx 库
2. 如果有，在 `Android.bp` 中添加依赖并取消注释代码
3. 如果没有，选择上述方案之一进行集成
4. 或实现备选的 Android 原生 SpeechRecognizer
