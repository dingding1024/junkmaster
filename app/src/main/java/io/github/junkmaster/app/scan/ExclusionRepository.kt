package io.github.junkmaster.app.scan

import io.github.junkmaster.app.clean.PathGuard
import java.util.Collections

/**
 * 用户白名单（永不删除的路径）。纯 JVM 内存态；持久化由 data/PreferencesRepository 负责。
 * 语义 deny > allow：即便规则命中，白名单路径也不会进入删除队列。
 */
class ExclusionRepository(initial: Set<String> = emptySet()) {

    private val denied = Collections.synchronizedSet(initial.toMutableSet())

    fun add(path: String) {
        if (path.isNotBlank()) denied.add(path)
    }

    fun remove(path: String) {
        denied.remove(path)
    }

    fun snapshot(): Set<String> = denied.toSet()

    /** 交给 PathGuard 的校验上下文（App 侧与 UserService 侧都可用） */
    fun guardContext(): PathGuard.Context = PathGuard.Context(deniedSubstrings = snapshot())
}
