#!/bin/bash
# 下载 sherpa-onnx AAR 文件的脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LIBS_DIR="$PROJECT_ROOT/libs"
AAR_URL="https://jitpack.io/com/github/k2-fsa/sherpa-onnx/1.12.20/sherpa-onnx-1.12.20.aar"
AAR_FILE="$LIBS_DIR/sherpa-onnx.aar"

echo "=========================================="
echo "下载 Sherpa-ONNX AAR 文件"
echo "=========================================="
echo ""

# 创建 libs 目录（如果不存在）
mkdir -p "$LIBS_DIR"

# 检查是否已存在
if [ -f "$AAR_FILE" ]; then
    echo "⚠️  文件已存在: $AAR_FILE"
    read -p "是否重新下载? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "跳过下载"
        exit 0
    fi
    rm -f "$AAR_FILE"
fi

echo "📥 正在从 JitPack 下载..."
echo "URL: $AAR_URL"
echo "目标: $AAR_FILE"
echo ""

# 尝试使用 wget
if command -v wget &> /dev/null; then
    echo "使用 wget 下载..."
    wget -O "$AAR_FILE" "$AAR_URL" || {
        echo "❌ wget 下载失败"
        exit 1
    }
# 尝试使用 curl
elif command -v curl &> /dev/null; then
    echo "使用 curl 下载..."
    curl -L -o "$AAR_FILE" "$AAR_URL" || {
        echo "❌ curl 下载失败"
        exit 1
    }
else
    echo "❌ 错误: 未找到 wget 或 curl"
    echo ""
    echo "请手动下载 AAR 文件:"
    echo "  URL: $AAR_URL"
    echo "  保存到: $AAR_FILE"
    exit 1
fi

# 验证文件
if [ -f "$AAR_FILE" ]; then
    FILE_SIZE=$(du -h "$AAR_FILE" | cut -f1)
    echo ""
    echo "✅ 下载成功!"
    echo "   文件: $AAR_FILE"
    echo "   大小: $FILE_SIZE"
    echo ""
    echo "📦 验证 AAR 文件内容..."
    if command -v unzip &> /dev/null; then
        if unzip -l "$AAR_FILE" | grep -q "classes.jar"; then
            echo "✅ AAR 文件格式正确（包含 classes.jar）"
        else
            echo "⚠️  警告: AAR 文件可能不完整"
        fi
    fi
    echo ""
    echo "✨ 完成! 现在可以编译项目了"
else
    echo "❌ 下载失败: 文件不存在"
    exit 1
fi
