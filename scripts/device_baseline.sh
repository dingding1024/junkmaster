#!/usr/bin/env bash
# =============================================================================
# 「安卓垃圾清扫大师」adb 环境下的可选基线采集
#
# 注意（2026-11-05）：测试方式已改为「用户自行侧载安装 + App 内测试诊断页回传报告」，
# 本脚本**不再是发布 gate 前提**，仅在恰好有 USB adb 环境时作为补充诊断手段。
# 设备与系统信息现在由 App 内「测试诊断」页采集，无需连接电脑。
#
# 默认行为：纯只读。只采集设备信息与日志，不安装 APK、不删除任何文件、
#           不执行 trim-caches、不改动设备设置。
#
# 用法：
#   bash scripts/device_baseline.sh                 # 只读基线采集
#   bash scripts/device_baseline.sh --install      # 额外安装 debug APK（写操作）
#   bash scripts/device_baseline.sh --probe-trim   # 额外实测 trim-caches（会真实清缓存，需确认）
#
# 覆盖 ADB 路径 / 序列号：
#   ADB_BIN=/path/to/adb SERIAL=<serial> bash scripts/device_baseline.sh
#
# 输出：docs/device-baseline/<时间戳>/ 下的多个 .txt，以及汇总 report.txt
# =============================================================================
set -u

ADB_BIN="${ADB_BIN:-D:/AndroidDev/Android/Sdk/platform-tools/adb.exe}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/docs/device-baseline/$STAMP"
PKG="io.github.junkmaster.app"
SHIZUKU_PKG="moe.shizuku.privileged.api"

DO_INSTALL=0
DO_TRIM=0
for arg in "$@"; do
  case "$arg" in
    --install) DO_INSTALL=1 ;;
    --probe-trim) DO_TRIM=1 ;;
    *) echo "未知参数: $arg"; exit 2 ;;
  esac
done

mkdir -p "$OUT" || exit 1

if [ ! -x "$ADB_BIN" ] && ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "ERROR: 找不到 adb: $ADB_BIN（可用 ADB_BIN=... 覆盖）"
  exit 2
fi

# 设备选择：未指定 SERIAL 时取第一台 device 状态设备
ADB=("$ADB_BIN")
"$ADB_BIN" devices -l > "$OUT/00-adb-devices.txt" 2>&1
SERIAL="${SERIAL:-}"
if [ -z "$SERIAL" ]; then
  SERIAL="$(awk '$2 == "device" {print $1; exit}' "$OUT/00-adb-devices.txt")"
fi
if [ -n "$SERIAL" ]; then
  ADB=("$ADB_BIN" -s "$SERIAL")
fi

if [ -z "$SERIAL" ]; then
  cat > "$OUT/report.txt" <<'EOF'
NO DEVICE / 未检测到真机

本次未采集任何数据。请按顺序完成：
  1. 用 USB 数据线连接魅族 21 Pro（不要用仅充电线）。
  2. 设置 -> 关于手机 -> 连续点「版本号」7 次开启开发者选项。
  3. 开发者选项 -> 开启「USB 调试」；Flyme 若提示「USB 调试(安全设置)」也需开启。
  4. 手机弹出「允许 USB 调试吗」时勾选「一律允许」并确认本机 RSA 指纹。
  5. 关闭「Flyme 支付保护」（Shizuku 官方 FAQ 对 Flyme 的要求）。
  6. 安装并启动 Shizuku v13.6.0，用无线调试或 ADB 方式启动服务。
  7. 重新运行：bash scripts/device_baseline.sh

注意：adb devices 显示 unauthorized / offline 都不算连上，需状态为 device。
EOF
  cat "$OUT/report.txt"
  exit 1
fi

echo "采集设备: $SERIAL"
echo "输出目录: $OUT"

# --- 只读采集 ---------------------------------------------------------------
run() {
  local name="$1"; shift
  {
    echo "# $*"
    echo "# 时间: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo
    "${ADB[@]}" "$@" 2>&1
  } > "$OUT/$name.txt"
}

run 01-all-props            shell getprop
run 02-sdk                  shell getprop ro.build.version.sdk
run 03-release              shell getprop ro.build.version.release
run 04-model                shell getprop ro.product.model
run 05-fingerprint          shell getprop ro.build.fingerprint
run 06-flyme-props          shell "getprop | grep -iE 'flyme|meizu|ro.build.display.id|ro.build.version.incremental'"
run 07-identity             shell "id; getenforce; getconf PAGESIZE; uname -a"
run 08-selinux              shell "getenforce; ls -Z /sdcard/Android/data 2>&1 | head -5"
run 09-shizuku-packages     shell "pm list packages | grep -i shizuku"
run 10-shizuku-version      shell "dumpsys package $SHIZUKU_PKG | grep -E 'versionName|versionCode|targetSdk|firstInstallTime' | head -20"
run 11-app-installed        shell "pm path $PKG"
run 12-app-version          shell "dumpsys package $PKG | grep -E 'versionName|versionCode|targetSdk|firstInstallTime' | head -20"
run 13-app-permissions      shell "dumpsys package $PKG | sed -n '/runtime permissions:/,/^$/p' | head -40"
run 14-appops-usage         shell "cmd appops get $PKG 2>/dev/null | head -20"
run 15-storage-paths        shell "ls -ld /sdcard /storage/emulated/0 /sdcard/Android/data /sdcard/Android/obb 2>&1"
run 16-available-space      shell "df -h /sdcard /data 2>&1"
run 17-logcat-tail          logcat -d -t 600
run 18-crash-anr            logcat -d -b crash -t 200

# 真机复验本 App 的 Manifest 权限（重点：不得有 INTERNET）
{
  echo "# 真机 Manifest 权限复验"
  "${ADB[@]}" shell dumpsys package "$PKG" 2>&1 \
    | grep -iE 'android.permission.INTERNET|MANAGE_EXTERNAL_STORAGE|PACKAGE_USAGE_STATS|API_V23' \
    | head -30
  if "${ADB[@]}" shell dumpsys package "$PKG" 2>&1 | grep -qi 'android.permission.INTERNET'; then
    echo "RESULT: FAIL - 出现 INTERNET 权限"
  else
    echo "RESULT: OK - 未声明 INTERNET 权限"
  fi
} > "$OUT/19-no-internet-check.txt" 2>&1

# --- 可选：安装 debug APK（写操作）-------------------------------------------
if [ "$DO_INSTALL" -eq 1 ]; then
  {
    echo "# adb install -r debug APK"
    "${ADB[@]}" install -r "$ROOT/安卓垃圾清扫大师-debug.apk" 2>&1
  } > "$OUT/20-install-debug.txt"
  echo "已尝试安装 debug APK，见 20-install-debug.txt"
fi

# --- 可选：trim-caches 实测（会真实清理缓存，须老板确认后才加该参数）---------
if [ "$DO_TRIM" -eq 1 ]; then
  {
    echo "# trim-caches 原始返回（实测会触发系统清理缓存，不可回退）"
    "${ADB[@]}" shell cmd package trim-caches 67108864 2>&1
    echo "--- pm trim-caches ---"
    "${ADB[@]}" shell pm trim-caches 67108864 2>&1
  } > "$OUT/21-trim-caches.txt"
  echo "已实测 trim-caches，见 21-trim-caches.txt"
fi

# --- 汇总 -------------------------------------------------------------------
{
  echo "M7 真机基线采集汇总"
  echo "时间: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "设备序列号: $SERIAL"
  echo "SDK:        $(tr -d '\r' < "$OUT/02-sdk.txt" | tail -1)"
  echo "Android:    $(tr -d '\r' < "$OUT/03-release.txt" | tail -1)"
  echo "机型:       $(tr -d '\r' < "$OUT/04-model.txt" | tail -1)"
  echo ""
  echo "文件清单:"
  ls -1 "$OUT" | grep -v '^report.txt$' | sed 's/^/  /'
  echo ""
  echo "下一步（人工在手机上完成，脚本不代劳）："
  echo "  1. 打开 App，授予「所有文件访问权」与「用量访问权」。"
  echo "  2. 不开 Shizuku：跑一次基础扫描 + L1 直清，确认基础模式独立可用。"
  echo "  3. 启动 Shizuku 并授权，回首页刷新，确认状态自动变为「已就绪」（冷启动自动绑定）。"
  echo "  4. 跑深度扫描，确认 Android/data/*/cache 可枚举且需 Shizuku 标记正确。"
  echo "  5. 逐项按 docs/TEST_REPORT.md 真机清单打勾，全部通过才可判 PASS。"
  echo ""
  echo "GATE: TEST_REPORT.md 判定 PASS 且老板确认前，禁止任何 GitHub 远程写操作。"
} > "$OUT/report.txt"

echo "完成，汇总见: $OUT/report.txt"
cat "$OUT/report.txt"
