package io.github.junkmaster.app.clean

import java.io.File

/** 可用空间探测。纯 JVM，用 java.io.File（Android 底层同样是 statfs），便于单测。 */
interface CapacityProbe {
    fun usableBytes(path: String): Long
    fun totalBytes(path: String): Long
}

class JvmCapacityProbe : CapacityProbe {
    override fun usableBytes(path: String): Long = try {
        File(path).usableSpace
    } catch (t: Throwable) {
        0L
    }

    override fun totalBytes(path: String): Long = try {
        File(path).totalSpace
    } catch (t: Throwable) {
        0L
    }
}
