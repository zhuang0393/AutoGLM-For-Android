# Sherpa-ONNX 库集成指南

## 问题描述

当前代码中 sherpa-onnx 库的导入和实现被注释掉了，导致语音识别功能无法正常工作。

## 解决方案

### 方案 1：使用 Gradle 构建（推荐）

项目已经在 `app/build.gradle.kts` 中配置了 sherpa-onnx 依赖：

```kotlin
implementation("com.github.k2-fsa:sherpa-onnx:1.12.20")
```

**步骤：**

1. **取消注释 import 语句**

在 `app/src/main/java/com/kevinluo/autoglm/voice/SherpaOnnxRecognizer.kt` 文件开头，取消注释以下导入：

```kotlin
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
```

2. **移除 stub 类型定义**

删除或注释掉这些行：
```kotlin
private typealias OfflineRecognizer = Any
private typealias Vad = Any
// ... 其他 stub 定义
```

3. **取消注释实际实现代码**

在 `initialize()` 方法中，取消注释 VAD 和 ASR 识别器的初始化代码（第 178-197 行和第 204-217 行）。

在 `recognize()` 方法中，取消注释识别逻辑（第 257-264 行）。

在 `recognizeWithVad()` 方法中，取消注释 VAD 识别逻辑（第 333-354 行）。

在 `isSpeechEnded()` 方法中，取消注释 VAD 检查逻辑（第 378-381 行）。

4. **同步 Gradle 依赖**

```bash
./gradlew build --refresh-dependencies
```

### 方案 2：系统构建（Android.bp）

如果使用系统构建，需要：

1. **添加预编译库到 Android.bp**

在 `Android.bp` 中添加 sherpa-onnx 的预编译库：

```bp
android_app {
    name: "AutoGLM",
    // ... 其他配置

    libs: [
        "sherpa-onnx",  // 需要先在系统中编译此库
    ],
}
```

2. **或者使用条件编译**

保持当前的 stub 实现，在运行时检测库是否可用，如果不可用则使用 Android 原生 SpeechRecognizer 作为备选方案。

### 方案 3：使用 Android 原生 SpeechRecognizer（备选）

如果 sherpa-onnx 库无法集成，可以使用 Android 系统内置的 `SpeechRecognizer` API：

**优点：**
- 无需额外依赖
- 系统级支持
- 自动处理网络连接

**缺点：**
- 需要网络连接（在线识别）
- 依赖 Google 服务
- 部分设备可能不支持离线识别

**实现示例：**

```kotlin
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.content.Intent
import android.speech.RecognizerIntent

// 在 VoiceInputManager 中添加备选方案
private fun useAndroidSpeechRecognizer() {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // 优先使用离线
    }

    if (SpeechRecognizer.isRecognitionAvailable(context)) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                // 处理识别结果
            }
            // ... 其他回调
        })
        recognizer.startListening(intent)
    }
}
```

## 当前状态

- ✅ Gradle 构建：依赖已配置，只需取消注释代码
- ❌ 系统构建：需要添加预编译库或使用备选方案
- ✅ 错误处理：已修复初始化失败时的错误提示

## 推荐操作

1. **如果使用 Gradle 构建**：直接取消注释 import 和实现代码
2. **如果使用系统构建**：考虑使用 Android 原生 SpeechRecognizer 作为备选
3. **混合方案**：优先使用 sherpa-onnx，失败时自动降级到原生 API

## 验证

取消注释后，重新编译并测试：

```bash
# Gradle 构建
./gradlew clean build

# 测试语音识别
# 应该能看到识别结果，而不是空结果
```

如果遇到编译错误，检查：
1. 依赖是否正确下载（检查 `~/.gradle/caches`）
2. 网络连接是否正常（JitPack 需要访问 GitHub）
3. 版本号是否正确（当前使用 1.12.20）
