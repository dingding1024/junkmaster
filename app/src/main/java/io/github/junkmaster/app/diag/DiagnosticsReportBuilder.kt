package io.github.junkmaster.app.diag

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.BuildConfig
import io.github.junkmaster.app.clean.JvmCapacityProbe
import io.github.junkmaster.app.clean.LastCleanSummary
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断报告拼装：把用户自测时我需要的可定位信息汇总成一段纯文本。
 *
 * 数据源全部是现成 API：ShizukuGateway.status / features、AppServices.basePermissions()、
 * JvmCapacityProbe()、DeleteJournal.files()、StorageProbe、last_clean.txt、崩溃记录。
 * 不读 IMEI / serial / ANDROID_ID，不读聊天内容、不逐条输出日志路径。
 * 报告只在内存里生成，不落公共目录；屏显 / 复制 / 分享使用同一份脱敏文本。
 *
 * report_format=2（第三轮）：新增可自证字段，让一份报告就能判断「0 删除」卡在
 *   PathGuard、目录权限还是删除执行。
 * report_format=3（第四轮）：新增「最近一次扫描分布」（只有分区数量与 ruleId 计数），
 *   并把清理日志里的「安全跳过」与真实删除失败分开统计 —— 上一轮 178 项全是空目录、
 *   安全跳过被记成失败 8 次并误报「系统性异常中止」，靠旧报告很难一眼看穿。
 * report_format=4（第五轮）：新增「绑定链路」段（last_bind_* / user_service_args / bind_trace）。
 *   第五轮报告出现 shizuku_ping_binder=true + permission=granted 但 status=服务未运行、
 *   uid=-1、raw_probe 为空 —— 说明授权与 binder 都正常，失败在 bindUserService 之后。
 *   旧报告看不出卡点，新报告直接列出每一步的结果、耗时与异常，无需 adb。
 */
class DiagnosticsReportBuilder(private val context: Context) {

    fun build(userNote: String): String {
        val sb = StringBuilder()
        section(sb, "报告头")
        line(sb, "generated", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        line(sb, "report_format", REPORT_FORMAT)
        line(sb, "app", "安卓垃圾清扫大师 / " + BuildConfig.APPLICATION_ID)
        line(sb, "version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
        line(sb, "build_type", BuildConfig.BUILD_TYPE)
        line(sb, "signer_sha256", signerSha256())

        section(sb, "设备与系统")
        line(sb, "manufacturer", Build.MANUFACTURER)
        line(sb, "model", Build.MODEL)
        line(sb, "brand", Build.BRAND)
        line(sb, "android", Build.VERSION.RELEASE)
        line(sb, "sdk", Build.VERSION.SDK_INT)
        line(sb, "display", Build.DISPLAY)
        line(sb, "fingerprint", Build.FINGERPRINT)

        section(sb, "权限状态")
        val (allFiles, usage) = try {
            AppServices.basePermissions()
        } catch (t: Throwable) {
            Pair(false, false)
        }
        line(sb, "all_files_access", allFiles)
        line(sb, "usage_stats", usage)
        line(sb, "notifications", notificationsState())

        section(sb, "Shizuku 状态与能力")
        val status = try {
            AppServices.shizuku.currentStatus().label
        } catch (t: Throwable) {
            "unknown"
        }
        val features = try {
            AppServices.shizuku.features.value
        } catch (t: Throwable) {
            null
        }
        line(sb, "status", status)
        line(sb, "shizuku_installed", shizukuInstalled())
        line(sb, "shizuku_ping_binder", shizukuPingBinder())
        line(sb, "uid", features?.uid ?: -1)
        line(sb, "sdk_from_userservice", features?.sdkInt ?: 0)
        line(sb, "shell_can_delete_app_data_cache", features?.shellCanDeleteAppDataCache ?: false)
        line(sb, "shizuku_permission", shizukuPermission())
        line(sb, "raw_probe", ReportSanitizer.sanitize(features?.rawProbe ?: "", 512))

        // 绑定链路（report_format=4）：定位「已授权但恒为服务未运行 / 点按钮无反应」
        val lastBind = try { AppServices.shizuku.lastBind.value } catch (t: Throwable) { null }
        line(sb, "last_bind_started", lastBind?.started ?: "none")
        line(sb, "last_bind_reason", ReportSanitizer.sanitize(lastBind?.reason ?: "none", 200))
        line(sb, "user_service_args", userServiceArgsSummary())
        sb.append("bind_trace:\n")
        val traceText = try {
            AppServices.shizuku.bindTraceText()
        } catch (t: Throwable) {
            "trace_error=" + t.javaClass.simpleName
        }
        sb.append(ReportSanitizer.sanitize(traceText, MAX_BIND_TRACE_CHARS)).append('\n')

        section(sb, "存储容量")
        val root = try { AppServices.storageRoot() } catch (t: Throwable) { "/" }
        val probe = JvmCapacityProbe()
        line(sb, "capacity_probe_partition", "external_storage")
        line(sb, "usable_bytes", probe.usableBytes(root))
        line(sb, "total_bytes", probe.totalBytes(root))

        section(sb, "存储与路径探针（实测，已脱敏；不输出任何路径）")
        val probeText = try {
            StorageProbe.lines(context).joinToString("\n")
        } catch (t: Throwable) {
            "probe_error=" + t.javaClass.simpleName
        }
        sb.append(ReportSanitizer.sanitize(probeText, MAX_PROBE_CHARS)).append('\n')

        section(sb, "最近一次扫描分布（已脱敏；只有计数与规则 ID，无路径）")
        val lastScan = try {
            LastScanSummary.read(AppServices.lastScanFile())
        } catch (t: Throwable) {
            ""
        }
        sb.append(
            if (lastScan.isBlank()) "无扫描记录（安装后尚未执行过只读扫描）\n"
            else ReportSanitizer.sanitize(lastScan, MAX_LAST_SCAN_CHARS).trimEnd() + "\n"
        )

        section(sb, "最近一次清理结果（已脱敏；无路径）")
        val lastClean = try {
            LastCleanSummary.read(AppServices.lastCleanFile())
        } catch (t: Throwable) {
            ""
        }
        sb.append(
            if (lastClean.isBlank()) "无清理记录（安装后尚未执行过一键清理）\n"
            else ReportSanitizer.sanitize(lastClean, MAX_LAST_CLEAN_CHARS).trimEnd() + "\n"
        )

        section(sb, "清理日志（聚合，已脱敏；不输出任何路径）")
        val files = try {
            AppServices.journalFiles()
        } catch (t: Throwable) {
            emptyList()
        }
        val lines = mutableListOf<String>()
        for (f in files) {
            try {
                f.forEachLine { if (lines.size < MAX_LOG_LINES) lines.add(it) }
            } catch (t: Throwable) {
                // 单个日志文件读失败不影响其它段
            }
        }
        val summary = ReportSanitizer.aggregateJournal(lines.asSequence())
        line(sb, "log_files", files.size)
        line(sb, "entries", summary.entryCount)
        line(sb, "success", summary.successCount)
        line(sb, "failed", summary.failedCount)
        line(sb, "skipped", summary.skippedCount)
        line(sb, "latest_ts", summary.latestTs)
        line(sb, "rules", summary.ruleCounts.entries.sortedBy { it.key }
            .joinToString(", ") { it.key + "=" + it.value }.ifBlank { "none" })
        line(
            sb, "fail_reasons",
            ReportSanitizer.sanitize(
                summary.failedReasonCounts.entries.sortedByDescending { it.value }
                    .joinToString(" | ") { it.key + " x" + it.value }.ifBlank { "none" },
                MAX_REASON_CHARS
            )
        )
        line(
            sb, "skip_reasons",
            ReportSanitizer.sanitize(
                summary.skippedReasonCounts.entries.sortedByDescending { it.value }
                    .joinToString(" | ") { it.key + " x" + it.value }.ifBlank { "none" },
                MAX_REASON_CHARS
            )
        )
        line(sb, "last_ok", summary.lastOk?.toString() ?: "none")
        line(sb, "last_rule", summary.lastRule.ifBlank { "none" })
        line(
            sb, "last_reason",
            ReportSanitizer.sanitize(summary.lastReason.ifBlank { "none" }, MAX_REASON_CHARS)
        )

        section(sb, "最近一次崩溃栈（已脱敏）")
        val crash = try {
            val f = AppServices.crashFile()
            if (f.isFile) f.readText(Charsets.UTF_8) else ""
        } catch (t: Throwable) {
            ""
        }
        sb.append(
            if (crash.isBlank()) "无崩溃记录\n"
            else ReportSanitizer.sanitize(crash, MAX_CRASH_CHARS) + "\n"
        )

        section(sb, "用户附注")
        sb.append(
            if (userNote.isBlank()) "（未填写）\n"
            else ReportSanitizer.sanitize(userNote, MAX_NOTE_CHARS) + "\n"
        )

        sb.append("\n提示：本报告已脱敏，发送前请自行检查；本应用不自动上传、不联网。\n")
        sb.append("提示：若 status 显示「未检测到 Shizuku 服务」，说明 Shizuku 只安装未启动；基础清扫不依赖它。\n")
        sb.append("提示：skipped 为防误删策略主动放弃的项，不是失败；only_empty_dirs=true 表示本次只清了不占空间的空目录。\n")
        sb.append("提示：bind_trace 记录绑定每一步（ping / 授权 / bindUserService / 回调 / probe），其中 FAIL 那行就是卡点。\n")
        return sb.toString()
    }

    private fun section(sb: StringBuilder, name: String) {
        sb.append("\n==== ").append(name).append(" ====\n")
    }

    private fun line(sb: StringBuilder, key: String, value: Any?) {
        sb.append(key).append('=').append(value?.toString() ?: "n/a").append('\n')
    }

    private fun signerSha256(): String {
        if (Build.VERSION.SDK_INT < 28) return "n/a (API<28)"
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val first = info.signingInfo?.apkContentsSigners?.firstOrNull()
            if (first == null) {
                "n/a"
            } else {
                MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
        } catch (t: Throwable) {
            "n/a (" + t.javaClass.simpleName + ")"
        }
    }

    private fun notificationsState(): String = try {
        if (Build.VERSION.SDK_INT < 33) {
            "n/a (API<33)"
        } else if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "granted"
        } else {
            "not_granted"
        }
    } catch (t: Throwable) {
        "unknown"
    }

    /** 区分「App 已安装」与「服务已启动」：老板真机上两者不是一回事。 */
    private fun shizukuInstalled(): String = try {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        "true"
    } catch (e: PackageManager.NameNotFoundException) {
        "false"
    } catch (t: Throwable) {
        "unknown (" + t.javaClass.simpleName + ")"
    }

    private fun shizukuPingBinder(): String = try {
        rikka.shizuku.Shizuku.pingBinder().toString()
    } catch (t: Throwable) {
        "unavailable (" + t.javaClass.simpleName + ")"
    }

    private fun shizukuPermission(): String = try {
        if (rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            "granted"
        } else {
            "not_granted"
        }
    } catch (t: IllegalStateException) {
        "unavailable (服务未运行，binder 不可用)"
    } catch (t: Throwable) {
        "unknown (" + t.javaClass.simpleName + ")"
    }

    /** 诊断报告用：UserServiceArgs 参数摘要（不含路径，可直接外发）。 */
    private fun userServiceArgsSummary(): String = try {
        AppServices.shizuku.userServiceArgsSummary()
    } catch (t: Throwable) {
        "unavailable (" + t.javaClass.simpleName + ")"
    }

    private companion object {
        const val REPORT_FORMAT: Int = 4
        const val MAX_LOG_LINES: Int = 5000
        const val MAX_CRASH_CHARS: Int = 8192
        const val MAX_NOTE_CHARS: Int = 2000
        const val MAX_PROBE_CHARS: Int = 4096
        const val MAX_LAST_CLEAN_CHARS: Int = 4096
        const val MAX_LAST_SCAN_CHARS: Int = 4096
        const val MAX_BIND_TRACE_CHARS: Int = 4096
        const val MAX_REASON_CHARS: Int = 1024
    }
}
