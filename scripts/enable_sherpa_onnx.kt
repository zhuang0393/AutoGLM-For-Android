#!/usr/bin/env kotlin
/**
 * 脚本：启用 sherpa-onnx 库
 *
 * 此脚本会自动取消注释 SherpaOnnxRecognizer.kt 中的 import 和实现代码
 *
 * 使用方法：
 *   kotlin scripts/enable_sherpa_onnx.kt
 *   或
 *   ./scripts/enable_sherpa_onnx.kt
 */

import java.io.File

val recognizerFile = File("app/src/main/java/com/kevinluo/autoglm/voice/SherpaOnnxRecognizer.kt")

if (!recognizerFile.exists()) {
    println("错误：找不到文件 ${recognizerFile.absolutePath}")
    exitProcess(1)
}

var content = recognizerFile.readText()

// 1. 取消注释 import 语句
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.OfflineRecognizer",
    "import com.k2fsa.sherpa.onnx.OfflineRecognizer"
)
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.Vad",
    "import com.k2fsa.sherpa.onnx.Vad"
)
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.VadModelConfig",
    "import com.k2fsa.sherpa.onnx.VadModelConfig"
)
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.SileroVadModelConfig",
    "import com.k2fsa.sherpa.onnx.SileroVadModelConfig"
)
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig",
    "import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig"
)
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.OfflineModelConfig",
    "import com.k2fsa.sherpa.onnx.OfflineModelConfig"
)
content = content.replace(
    "// import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig",
    "import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig"
)

// 2. 注释掉 stub 类型定义
content = content.replace(
    "private typealias OfflineRecognizer = Any",
    "// private typealias OfflineRecognizer = Any"
)
content = content.replace(
    "private typealias Vad = Any",
    "// private typealias Vad = Any"
)
content = content.replace(
    "private typealias VadModelConfig = Any",
    "// private typealias VadModelConfig = Any"
)
content = content.replace(
    "private typealias SileroVadModelConfig = Any",
    "// private typealias SileroVadModelConfig = Any"
)
content = content.replace(
    "private typealias OfflineRecognizerConfig = Any",
    "// private typealias OfflineRecognizerConfig = Any"
)
content = content.replace(
    "private typealias OfflineModelConfig = Any",
    "// private typealias OfflineModelConfig = Any"
)
content = content.replace(
    "private typealias OfflineParaformerModelConfig = Any",
    "// private typealias OfflineParaformerModelConfig = Any"
)

// 3. 取消注释 VAD 初始化代码（需要手动处理多行注释）
// 这部分需要手动编辑，因为涉及多行注释块

// 4. 取消注释 ASR 识别器初始化代码
// 这部分需要手动编辑

// 5. 取消注释识别逻辑
// 这部分需要手动编辑

recognizerFile.writeText(content)
println("已更新文件：${recognizerFile.absolutePath}")
println("注意：还需要手动取消注释多行注释块中的实现代码")
println("请查看 SHERPA_ONNX_SETUP.md 获取详细说明")
