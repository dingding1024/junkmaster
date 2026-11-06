package io.github.junkmaster.app.scan

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * 通过 MediaStore 列举 Download 内容（READ_MEDIA_* 仅用于列举）。
 * 产出恒为 REPORT_ONLY，绝不删除。只读 dry-run。
 */
class MediaStoreScanner(
    private val context: Context,
    private val evaluator: RuleEvaluator = RuleEvaluator()
) : ItemSource {

    override val name: String = "Download（MediaStore，仅报告）"

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.RELATIVE_PATH
        )
        val cursor = try {
            context.contentResolver.query(uri, projection, null, null, null)
        } catch (t: Throwable) {
            null
        } ?: return

        cursor.use { c ->
            val nameIdx = c.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.Downloads.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)
            val relIdx = c.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
            while (!session.cancelled && c.moveToNext()) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                if (name != null) {
                    val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                    val date = if (dateIdx >= 0 && !c.isNull(dateIdx)) c.getLong(dateIdx) * 1000L else 0L
                    val rel = if (relIdx >= 0) (c.getString(relIdx) ?: "Download/") else "Download/"
                    val path = "/sdcard/" + rel.trimEnd('/') + "/" + name
                    emit(evaluator.toScanItem(path, size, date, ScanCategory.DOWNLOAD_LARGE))
                }
            }
        }
    }
}
