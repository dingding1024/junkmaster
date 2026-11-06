package io.github.junkmaster.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文件大小的中文友好显示（纯 JVM）。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.size - 1) {
        value /= 1024.0
        index++
    }
    return String.format("%.1f %s", value, units[index])
}

/** 时间戳 → 「2026-08-01 21:30」；非法 / 0 返回「未知」（纯 JVM）。 */
fun formatDateTime(ms: Long): String {
    if (ms <= 0L) return "未知"
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))
    } catch (t: Throwable) {
        "未知"
    }
}

/**
 * 距今多少天的中文表述（大文件索引用）。
 * 0 天传 "今天"，1 天 "1 天前"，>=30 天给「N 个月」（按 30 天一个月粗算，
 * 这里只是给人看的粗略量级，不参与任何判定）。
 */
fun formatAgeDays(days: Int): String = when {
    days <= 0 -> "今天"
    days < 30 -> "$days 天前"
    days < 365 -> "${days / 30} 个月前"
    else -> "${days / 365} 年前"
}
