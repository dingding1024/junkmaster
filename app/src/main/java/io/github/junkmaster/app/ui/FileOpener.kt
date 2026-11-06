package io.github.junkmaster.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * 「在文件管理器中打开所在目录」（第七轮反馈第 3 条）。
 *
 * 只用系统 DocumentsUI 的标准 external storage document URI，**不申请任何新权限、
 * 不自带文件浏览器、不读文件内容**：把路径换成 content URI 交给系统文件管理器，
 * 用户自己看。设备上没有能处理该 URI 的应用时返回 false，由调用方提示改用复制路径。
 *
 * 传目录进去就打开目录本身，传文件进去打开它所在的父目录 —— 用户要的是
 * 「这个文件到底在手机的哪个位置」，落到父目录再高亮更符合直觉。
 */
object FileOpener {

    private const val AUTHORITY = "com.android.externalstorage.documents"

    fun open(context: Context, absPath: String, isDirectory: Boolean): Boolean {
        val root = try {
            Environment.getExternalStorageDirectory().absolutePath.replace('\\', '/')
        } catch (t: Throwable) {
            return false
        }
        val normalized = absPath.replace('\\', '/')
        // 只处理共享存储内的路径；/data、/system 之类系统文件管理器也打不开
        if (!normalized.startsWith(root)) return false

        var relative = normalized.removePrefix(root).trimStart('/')
        if (!isDirectory) {
            relative = if ('/' in relative) relative.substringBeforeLast('/') else ""
        }
        val docId = "primary:" + relative
        val uri = Uri.parse("content://" + AUTHORITY + "/document/" + Uri.encode(docId))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
