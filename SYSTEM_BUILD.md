# AutoGLM-For-Android 系统编译指南

本文档说明如何将 AutoGLM-For-Android 项目改造为在 Android 系统源码中编译，使用 System API 替代 Shizuku。

## 改造概述

### 可行性评估

✅ **可行** - 项目已成功改造为支持系统源码编译。

### 主要改动

1. **创建 SystemService** - 使用 System API 替代 Shizuku UserService
   - 使用 `Instrumentation` API 执行触摸和按键操作
   - 使用系统权限执行 shell 命令
   - 使用 `ActivityManager` 启动应用
   - 使用 `WindowManager` 获取当前应用信息

2. **修改 ComponentManager** - 支持系统模式初始化
   - 添加 `initializeSystemService()` 方法
   - 自动检测系统模式并初始化

3. **修改 MainActivity** - 条件化 Shizuku 相关代码
   - 添加 `isSystemBuild()` 检测方法
   - 系统模式下自动初始化 SystemService
   - 隐藏 Shizuku 状态 UI

    - 移除 Shizuku 依赖
    - 创建 Android.bp 用于系统编译，并静态链接所需的 AndroidX 库
    - 更新 AndroidManifest.xml 添加系统权限

## 编译方法

### 在 Android 系统源码中编译

1. **将项目放入系统源码目录**

```bash
# 假设你的项目在 vendor/semidrive/packages/AutoGLM-For-Android
# 确保 Android.bp 文件在项目根目录
```

2. **添加到产品配置**

在你的产品 `device.mk` 或 `product.mk` 中添加：

```makefile
PRODUCT_PACKAGES += AutoGLM
```

3. **编译**

```bash
# 在 Android 源码根目录执行
source build/envsetup.sh
lunch <your-product>
m AutoGLM
```

### 使用 Android.bp

项目已包含 `Android.bp` 文件，配置了：
- Platform 签名（`certificate: "platform"`）
- 系统应用权限（`privileged: true`）
- System API（`sdk_version: "system_current"`）
- 静态链接所需的 AndroidX 库

## 系统权限说明

应用需要以下系统权限（已在 AndroidManifest.xml 中配置）：

- `android.permission.INJECT_EVENTS` - 注入触摸和按键事件
- `android.permission.READ_FRAME_BUFFER` - 读取屏幕帧缓冲（截图）
- `android.permission.CAPTURE_SECURE_VIDEO_OUTPUT` - 捕获安全视频输出

## 功能对比

| 功能 | Shizuku 模式 | 系统模式 |
|------|-------------|---------|
| 触摸操作 | Shell 命令 `input tap` | `Instrumentation.sendPointerSync()` |
| 滑动操作 | Shell 命令 `input swipe` | `Instrumentation.sendPointerSync()` |
| 按键操作 | Shell 命令 `input keyevent` | `Instrumentation.sendKeySync()` |
| 截图 | Shell 命令 `screencap` | Shell 命令（系统权限） |
| 启动应用 | Shell 命令 `am start` | `ActivityManager` + `Intent` |
| 获取当前应用 | Shell 命令 `dumpsys window` | `ActivityManager.appTasks` |
| 输入法切换 | Shell 命令 `ime set` | Shell 命令（系统权限） |

## 注意事项

1. **系统签名** - 应用必须使用 platform 签名才能在系统模式下运行
2. **UID 检测** - `isSystemBuild()` 方法通过检查 UID 和权限来判断是否在系统模式
3. **向后兼容** - 代码同时支持 Shizuku 模式和系统模式，通过运行时检测自动切换
4. **Shizuku UI** - 在系统模式下，Shizuku 状态卡片会自动隐藏

## 测试

在系统模式下：
1. 应用启动时自动检测系统模式
2. 自动初始化 SystemService
3. 无需 Shizuku 授权即可使用所有功能
4. UI 中不显示 Shizuku 相关状态

## 故障排除

### 权限问题

如果遇到权限错误，检查：
1. 应用是否使用 platform 签名
2. AndroidManifest.xml 中是否包含系统权限
3. 应用是否在 `/system/priv-app/` 目录下
4. 检查 `permissions_com.kevinluo.autoglm.xml` 是否正确安装到 `/product/etc/permissions/` 或 `/system/etc/permissions/`


### 编译错误

如果 Android.bp 编译失败或运行时出现 ClassNotFoundException：
1. 检查依赖库是否在系统源码中可用
2. 可能需要调整 `static_libs` 和 `libs` 列表
3. 检查 Kotlin 和 Java 版本兼容性
4. **重要**：如果修改了 `static_libs` 后仍然报错，请尝试执行 `m installclean` 清清理旧的构建产物。

### 运行时权限错误
如果启动时遇到 `java.lang.IllegalStateException: Signature|privileged permissions not in privileged permission allowlist`：
1. 这说明 `permissions_com.kevinluo.autoglm.xml` 文件未正确更新到系统镜像中。
2. 请务必执行 **`m installclean`** 或手动删除输出目录 (`out/target/product/.../product/etc/permissions/permissions_com.kevinluo.autoglm.xml`)。
3. 重新编译 `m AutoGLM` 并刷入设备。
4. 确保 `Android.bp` 中的 Permission XML module (`prebuilt_etc`) 包含 `product_specific: true`，且位置正确。

## 回退到 Shizuku 模式

如果需要回退到 Shizuku 模式：
1. 恢复 `app/build.gradle.kts` 中的 Shizuku 依赖
2. 恢复 `AndroidManifest.xml` 中的 Shizuku provider
3. 在非系统环境下运行，会自动使用 Shizuku 模式
