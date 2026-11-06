package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * 扫描来源。所有实现必须 **只读 dry-run，绝不删除**。
 * emit 出来的 ScanItem 自带安全徽标所需字段与 consequenceId。
 */
interface ItemSource {
    val name: String
    fun collect(session: ScanSession, emit: (ScanItem) -> Unit)
}

/** 基于 FileEntry 快照的过滤型来源基类（纯 JVM，便于单测注入假数据）。 */
abstract class FilteringItemSource(
    override val name: String,
    private val provider: EntriesProvider,
    protected val evaluator: RuleEvaluator = RuleEvaluator()
) : ItemSource {

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        provider().forEach { entry ->
            if (session.cancelled) return
            val item = map(entry) ?: return@forEach
            emit(item)
        }
    }

    protected abstract fun map(entry: FileEntry): ScanItem?
}
