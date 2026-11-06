package io.github.junkmaster.app.scan

import io.github.junkmaster.app.clean.StorageRoots
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * L1：空目录（只读 dry-run）。基于条目快照判断「没有任何子条目」，纯 JVM 可测。
 *
 * 2026-11-05 真机修正（第三轮反馈）：
 *  - `readable=false` 的目录 = listFiles() 返回 null（无权限 / 骨架目录），**不是空目录**。
 *    典型是 /sdcard/Android/data：旧版把它误判为空目录并默认勾选，删除必然失败并触发失败即停。
 *  - 共享存储结构目录（存储根、一级目录、Android/data|obb|media）一律排除，
 *    即便快照看起来为空也不允许作为 L1 删除目标。
 *  - 真正删除时 NioFileDeleter 会再次确认目录「可列举且确实为空」，防止扫描后又有文件写入。
 */
class EmptyDirFinder(
    private val provider: EntriesProvider,
    private val evaluator: RuleEvaluator = RuleEvaluator()
) : ItemSource {

    override val name: String = "空目录（L1）"

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        val entries = provider()
        entries.filter {
            it.isDirectory &&
                    it.readable &&
                    !StorageRoots.isStructuralStorageDir(it.path)
        }.forEach { dir ->
            if (session.cancelled) return
            val prefix = dir.path.trimEnd('/') + "/"
            val hasChild = entries.any { e -> e.path != dir.path && e.path.startsWith(prefix) }
            if (!hasChild) {
                emit(
                    evaluator.toScanItem(
                        dir.path,
                        0L,
                        dir.lastModifiedMs,
                        ScanCategory.EMPTY_DIR
                    )
                )
            }
        }
    }
}
