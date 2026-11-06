package io.github.junkmaster.app.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * SAF 目录树只读列举（单级，避免引入 androidx.documentfile 依赖）。
 * 产出恒为 REPORT_ONLY：SAF 授权树只用于列举与报告，本 App 不通过 SAF 删除。
 */
class SafTreeScanner(
    private val context: Context,
    private val treeUri: Uri,
    private val evaluator: RuleEvaluator = RuleEvaluator()
) : ItemSource {

    override val name: String = "SAF 目录树（只读）"

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        if (session.cancelled) return
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
        } catch (t: Throwable) {
            return
        }
        val cursor = try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )
        } catch (t: Throwable) {
            null
        } ?: return

        cursor.use { c ->
            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val dateIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (!session.cancelled && c.moveToNext()) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                if (name != null) {
                    val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                    val date = if (dateIdx >= 0 && !c.isNull(dateIdx)) c.getLong(dateIdx) else 0L
                    val path = "/sdcard/SAF/" + name
                    emit(evaluator.toScanItem(path, size, date, ScanCategory.DOWNLOAD_LARGE))
                }
            }
        }
    }
}
