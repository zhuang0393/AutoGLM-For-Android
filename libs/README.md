# 预编译库目录

此目录用于存放预编译的 AAR 库文件。

## Sherpa-ONNX 库

### 下载方式

1. **从 JitPack 下载**（推荐）：
   ```bash
   # 下载 AAR 文件
   wget https://jitpack.io/com/github/k2-fsa/sherpa-onnx/1.12.20/sherpa-onnx-1.12.20.aar
   mv sherpa-onnx-1.12.20.aar libs/sherpa-onnx.aar
   ```

2. **从 Maven Central 下载**：
   ```bash
   # 如果 Maven Central 有发布
   wget https://repo1.maven.org/maven2/com/github/k2-fsa/sherpa-onnx/1.12.20/sherpa-onnx-1.12.20.aar
   mv sherpa-onnx-1.12.20.aar libs/sherpa-onnx.aar
   ```

3. **使用 Gradle 下载后复制**：
   ```bash
   # 在 Gradle 项目中执行
   ./gradlew :app:dependencies
   # 然后从 ~/.gradle/caches/modules-2/files-2.1/ 复制 AAR 文件
   ```

### 文件位置

将下载的 AAR 文件重命名为 `sherpa-onnx.aar` 并放在此目录下。
