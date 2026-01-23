# 截图功能调用流程

## 当前使用的截图方式

**setSkipScreenshot + AccessibilityService** 方式（优先使用 AccessibilityService，Shell 作为 fallback）

## 完整函数调用流程

```
PhoneAgent.executeStep()
    │
    └─> ScreenshotService.capture()
            │
            ├─> [检查] 是否有悬浮窗 && API >= 30 (Android 11+)
            │
            ├─> [是] 使用 setSkipScreenshot 方式
            │       │
            │       ├─> FloatingWindowController.getSurfaceControl()
            │       │       └─> [反射] View.getViewRootImpl()
            │       │               └─> [反射] ViewRootImpl.mSurfaceControl
            │       │
            │       ├─> [反射] SurfaceControl.Transaction.setSkipScreenshot(surfaceControl, true)
            │       │
            │       ├─> delay(100ms)  // 确保 setSkipScreenshot 生效
            │       │
            │       └─> captureScreen()
            │               │
            │               ├─> [优先] AutoGLMAccessibilityService.captureScreenshot()
            │               │       │
            │               │       ├─> AccessibilityService.takeScreenshot()
            │               │       │
            │               │       ├─> ScreenshotResult.hardwareBuffer
            │               │       │
            │               │       ├─> Bitmap.wrapHardwareBuffer()  // 硬件 Bitmap
            │               │       │
            │               │       └─> Bitmap.copy(ARGB_8888, false)  // 转换为软件 Bitmap
            │               │
            │               └─> [回退] 如果 AccessibilityService 失败
            │                       │
            │                       └─> executeScreencapToBytes()  // Shell 命令方式
            │                               │
            │                               ├─> userService.executeCommand("screencap -d 0 -p /path/to/file.png")
            │                               │
            │                               ├─> File.readBytes()  // 读取 PNG 文件
            │                               │
            │                               └─> BitmapFactory.decodeByteArray()  // 解码为 Bitmap
            │
            └─> [否] 使用 hide/show 方式 (降级方案)
                    │
                    └─> captureWithHideShow()
                            │
                            ├─> FloatingWindowController.hideFloatingWindow()
                            ├─> delay(200ms)
                            ├─> captureScreen()  // 同样优先使用 AccessibilityService
                            ├─> delay(100ms)
                            └─> FloatingWindowController.showFloatingWindow()

captureScreen() 后续处理:
    │
    ├─> [可选] 保存调试文件: /sdcard/Android/data/com.kevinluo.autoglm/screenshot_debug_{timestamp}.png
    │   │       (默认关闭，通过 ENABLE_DEBUG_SCREENSHOT 控制)
    │
    ├─> calculateOptimalDimensions()  // 计算缩放尺寸
    │
    ├─> Bitmap.createScaledBitmap()  // 缩放 Bitmap (如果需要)
    │
    ├─> bitmap.compress(WEBP_FORMAT, 65)  // 转换为 WebP
    │
    ├─> Base64.encode()  // 编码为 Base64
    │
    └─> 返回 Screenshot 对象

finally 块:
    │
    └─> [恢复] SurfaceControl.Transaction.setSkipScreenshot(surfaceControl, false)
```

## 关键决策点

### 1. 截图方式选择
```
if (有悬浮窗 && API >= 30) {
    使用 setSkipScreenshot 方式
} else {
    使用 hide/show 方式
}
```

### 2. Bitmap 获取方式（在 captureScreen 中）
```
[统一策略] 无论是否使用 setSkipScreenshot，都遵循以下优先级：

1. 优先尝试: AccessibilityService.captureScreenshot()
   └─> 成功 → 返回 Bitmap
   └─> 失败 → 继续

2. 回退方案: executeScreencapToBytes() (Shell 命令)
   └─> 成功 → 返回 Bitmap
   └─> 失败 → 返回 fallback screenshot
```

## 当前实际执行路径

```
✅ setSkipScreenshot(true) 设置成功
✅ 优先尝试 AccessibilityService
✅ AccessibilityService 成功获取 Bitmap
✅ setSkipScreenshot 对 AccessibilityService 有效（悬浮窗被排除）
✅ Shell 方式作为 fallback（通常不需要）
```

## 各方法说明

### 1. AccessibilityService 方式（优先）
- **API**: `AccessibilityService.takeScreenshot()`
- **要求**: Android R (API 30)+
- **优点**:
  - 系统级 API，稳定可靠
  - 与 setSkipScreenshot 兼容
  - 不需要 shell 权限
- **特点**: 返回 HardwareBuffer，需要转换为软件 Bitmap

### 2. Shell 方式 (`executeScreencapToBytes`) - Fallback
- **命令**: `screencap -d 0 -p /path/to/file.png`
- **优点**: 作为 fallback 方案
- **缺点**: 需要 shell 权限，可能失败
- **使用场景**: 仅在 AccessibilityService 不可用或失败时使用

### 3. setSkipScreenshot
- **API**: `SurfaceControl.Transaction.setSkipScreenshot()` (隐藏 API)
- **作用**: 在截屏时排除指定 Surface
- **使用**: 通过反射调用
- **效果**: 悬浮窗不会出现在截图中
- **兼容性**: 对 AccessibilityService 和 Shell 方式都有效

## 文件保存位置

- **调试文件**: `/sdcard/Android/data/com.kevinluo.autoglm/screenshot_debug_{timestamp}.png`
  - 默认关闭（`ENABLE_DEBUG_SCREENSHOT = false`）
  - 可通过修改常量启用
- **临时文件**: `/data/local/tmp/screenshot_{timestamp}.png` (Shell 方式使用，会自动清理)

## 性能优化

1. **setSkipScreenshot**: 避免隐藏/显示窗口，性能更好
2. **延迟处理**: 100ms 延迟确保 setSkipScreenshot 生效
3. **WebP 压缩**: 65% 质量，平衡文件大小和画质
4. **智能缩放**: 根据最大尺寸自动缩放，保持宽高比
5. **优先级策略**: 优先使用更稳定的 AccessibilityService，减少失败率

## 日志优化

- Shell 方式失败时静默失败，不输出详细错误日志
- 减少不必要的调试信息输出
- 只保留关键的成功/失败日志
