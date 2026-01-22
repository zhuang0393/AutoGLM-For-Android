# Sherpa-ONNX 集成指南（Android.bp 系统构建）

## 已完成的工作

✅ **代码修改**：
- 取消注释了所有 sherpa-onnx 的 import 语句
- 取消注释了所有实现代码（VAD、ASR 初始化、识别逻辑等）
- 移除了条件编译的 stub 代码

✅ **Android.bp 配置**：
- 创建了 `libs/Android.bp` 用于导入预编译的 AAR 库
- 在 `Android.bp` 中添加了 `libs: ["sherpa-onnx-prebuilt"]` 依赖

## 需要完成的步骤

### 1. 下载 Sherpa-ONNX AAR 文件

您需要下载 sherpa-onnx 的 AAR 文件并放到 `libs/` 目录下：

```bash
# 方法 1：使用 wget 直接下载
cd libs
wget https://jitpack.io/com/github/k2-fsa/sherpa-onnx/1.12.20/sherpa-onnx-1.12.20.aar
mv sherpa-onnx-1.12.20.aar sherpa-onnx.aar

# 方法 2：使用 Gradle 下载后复制
# 在 Gradle 项目中执行：
# ./gradlew :app:dependencies
# 然后从 ~/.gradle/caches/modules-2/files-2.1/com/github/k2-fsa/sherpa-onnx/1.12.20/ 复制 AAR 文件
# 重命名为 sherpa-onnx.aar 并放到 libs/ 目录
```

### 2. 验证文件结构

确保文件结构如下：

```
AutoGLM-For-Android/
├── Android.bp                    # 主构建文件（已修改）
├── libs/
│   ├── Android.bp                # 预编译库定义（已创建）
│   ├── README.md                 # 说明文件（已创建）
│   └── sherpa-onnx.aar          # AAR 文件（需要下载）
└── app/
    └── src/
        └── main/
            └── java/
                └── com/kevinluo/autoglm/
                    └── voice/
                        └── SherpaOnnxRecognizer.kt  # 已启用实现
```

### 3. 编译测试

```bash
# 在 Android 源码根目录执行
source build/envsetup.sh
lunch <your-product>
m AutoGLM
```

## 如果遇到编译错误

### 错误 1：找不到 sherpa-onnx-prebuilt

**原因**：`libs/Android.bp` 没有被系统构建系统识别

**解决**：
1. 确保 `libs/Android.bp` 文件存在
2. 检查 AAR 文件路径是否正确
3. 尝试在 `Android.bp` 中使用绝对路径或相对路径

### 错误 2：找不到类 com.k2fsa.sherpa.onnx.*

**原因**：AAR 文件未正确加载或路径错误

**解决**：
1. 验证 `libs/sherpa-onnx.aar` 文件是否存在
2. 检查 `libs/Android.bp` 中的路径是否正确
3. 尝试解压 AAR 文件验证内容：
   ```bash
   cd libs
   unzip -l sherpa-onnx.aar
   # 应该看到 classes.jar 文件
   ```

### 错误 3：Native 库加载失败

**原因**：sherpa-onnx 依赖 native 库（ONNX Runtime），可能需要额外的配置

**解决**：
1. 检查 AAR 文件中是否包含 native 库（libs/ 目录）
2. 如果缺少 native 库，可能需要：
   - 使用包含 native 库的完整版本
   - 或单独编译 native 库并添加到系统

## 备选方案

如果预编译 AAR 方案不可行，可以考虑：

1. **在系统源码中编译 sherpa-onnx**：
   - 将 sherpa-onnx 源码添加到系统源码树
   - 创建 Android.bp 编译 Java 和 native 部分

2. **使用 Android 原生 SpeechRecognizer**：
   - 无需额外依赖
   - 支持在线识别
   - 作为临时方案使用

## 验证集成

编译成功后，运行应用并测试语音识别：

1. 点击"语音输入"按钮
2. 说话进行录音
3. 检查 logcat 中是否有识别结果
4. 确认识别文字是否正确显示

如果看到以下日志，说明集成成功：
```
I AutoGLM/SherpaOnnxRecognizer: [Performance] SherpaOnnxRecognizer initialized successfully
I AutoGLM/VoiceInputManager: [Performance] Recognition completed, result: <识别文字>
```

## 注意事项

1. **AAR 文件大小**：sherpa-onnx AAR 可能较大（几十 MB），确保有足够空间
2. **Native 库**：如果 AAR 不包含 native 库，可能需要额外配置
3. **版本兼容性**：确保 AAR 版本与代码中使用的 API 兼容
4. **系统构建限制**：某些系统构建环境可能不支持 `android_library_import`，需要调整方案
