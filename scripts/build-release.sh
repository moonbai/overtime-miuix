#!/usr/bin/env bash
# 本地一键构建正式 Release APK（与双 CI 一致：assembleRelease + R8 + 签名 + 产物校验）
#
# 用法：
#   export KEYSTORE_BASE64=$(base64 -w0 /path/to/release.jks)   # macOS 用 base64 -i ... | tr -d '\n'
#   export KEYSTORE_PASSWORD='...' KEY_ALIAS='Mars' KEY_PASSWORD='...'
#   ./scripts/build-release.sh
#
# 也可把 4 项写入 local.properties（已 gitignore）并把 .jks 放到 app/release-keystore.jks。
# 密钥严禁写入仓库、gradle.properties 或本脚本。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.daemon=false -Dfile.encoding=UTF-8}"

BT="$ANDROID_HOME/build-tools/37.0.0"
if [ ! -x "$BT/apksigner" ]; then
  echo "!! 未找到 build-tools 37.0.0：$BT" >&2
  echo "   先执行：sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0'" >&2
  exit 1
fi

if [ -z "${KEYSTORE_BASE64:-}" ] && [ ! -f app/release-keystore.jks ]; then
  echo "!! 未检测到签名配置（KEYSTORE_BASE64 或 app/release-keystore.jks）" >&2
  echo "   Release 包必须签名，请配置后重试。" >&2
  exit 1
fi

echo "==> assembleRelease（R8 代码压缩 + 资源压缩 + 签名）"
./gradlew assembleRelease --no-daemon --stacktrace

APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
echo "==> 产物：$APK ($(du -h "$APK" | cut -f1))"
echo "==> 签名："
"$BT/apksigner" verify --print-certs "$APK" | head -8
echo "==> 元信息："
"$BT/aapt" dump badging "$APK" | head -2
echo "==> SHA256："
sha256sum "$APK"
echo "==> 构建完成"
