package io.github.junkmaster.app.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

// 事务码：文件顶层，Stub 与 Proxy 共用
private val T_PROBE: Int = IBinder.FIRST_CALL_TRANSACTION
private val T_LIST_CANDIDATES: Int = IBinder.FIRST_CALL_TRANSACTION + 1
private val T_DELETE_PATHS: Int = IBinder.FIRST_CALL_TRANSACTION + 2
private val T_TRIM_CACHES: Int = IBinder.FIRST_CALL_TRANSACTION + 3
private val T_CLEAR_CACHE_VIA_BINDER: Int = IBinder.FIRST_CALL_TRANSACTION + 4
private val T_QUERY_CACHE_BYTES: Int = IBinder.FIRST_CALL_TRANSACTION + 5
private val T_DESTROY: Int = IBinder.FIRST_CALL_TRANSACTION + 6

/**
 * 手写 Binder 接口（替代 AIDL 编译）。
 *
 * 为什么不用 AIDL：AGP 8.11.1 在 Windows + 中文项目路径下，AidlCompile 会用 UTF-8
 * 解码 aidl.exe 生成的依赖文件并抛 MalformedInputException（DependencyData.parseDependencyFile），
 * 且 Shizuku UserService 只需要一个实现 IBinder 的类，并不要求必须由 AIDL 生成。
 * 手写 Binder 功能等价、无编码坑、CI 与本地构建都更稳。
 *
 * 协议：每个方法一个事务码；自定义 Parcelable 直接 writeToParcel / CREATOR.createFromParcel，
 * 不写 readException 所需的头部，两端保持一致即可。
 */
interface ICleanerUserService : IInterface {
    fun probe(): ShellResult
    fun listCandidates(filterJson: String): List<String>
    fun deletePaths(paths: List<String>): ShellResult
    fun trimCaches(desiredBytes: Long): TrimResult
    fun clearCacheViaBinder(packageName: String): ShellResult
    fun queryCacheBytes(packageName: String): Long
    fun destroy()
}

abstract class CleanerServiceStub : Binder(), ICleanerUserService {

    init {
        attachInterface(this, INTERFACE_TOKEN)
    }

    override fun asBinder(): IBinder = this

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(INTERFACE_TOKEN)
            return true
        }
        return when (code) {
            T_PROBE -> {
                data.enforceInterface(INTERFACE_TOKEN)
                val result = probe()
                reply?.let { result.writeToParcel(it, 0) }
                true
            }
            T_LIST_CANDIDATES -> {
                data.enforceInterface(INTERFACE_TOKEN)
                val filter = data.readString() ?: ""
                val result = listCandidates(filter)
                reply?.writeInt(result.size)
                result.forEach { reply?.writeString(it) }
                true
            }
            T_DELETE_PATHS -> {
                data.enforceInterface(INTERFACE_TOKEN)
                val paths = data.createStringArrayList() ?: arrayListOf()
                val result = deletePaths(paths)
                reply?.let { result.writeToParcel(it, 0) }
                true
            }
            T_TRIM_CACHES -> {
                data.enforceInterface(INTERFACE_TOKEN)
                val desired = data.readLong()
                val result = trimCaches(desired)
                reply?.let { result.writeToParcel(it, 0) }
                true
            }
            T_CLEAR_CACHE_VIA_BINDER -> {
                data.enforceInterface(INTERFACE_TOKEN)
                val pkg = data.readString() ?: ""
                val result = clearCacheViaBinder(pkg)
                reply?.let { result.writeToParcel(it, 0) }
                true
            }
            T_QUERY_CACHE_BYTES -> {
                data.enforceInterface(INTERFACE_TOKEN)
                val pkg = data.readString() ?: ""
                reply?.writeLong(queryCacheBytes(pkg))
                true
            }
            T_DESTROY -> {
                data.enforceInterface(INTERFACE_TOKEN)
                destroy()
                reply?.writeInt(0)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private class Proxy(private val remote: IBinder) : ICleanerUserService {

        override fun asBinder(): IBinder = remote

        private inline fun <T> transact(
            code: Int,
            write: (Parcel) -> Unit,
            read: (Parcel) -> T
        ): T {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(INTERFACE_TOKEN)
                write(data)
                remote.transact(code, data, reply, 0)
                read(reply)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun probe(): ShellResult = transact(T_PROBE, {}) { reply ->
            ShellResult.CREATOR.createFromParcel(reply)
        }

        override fun listCandidates(filterJson: String): List<String> =
            transact(T_LIST_CANDIDATES, { it.writeString(filterJson) }) { reply ->
                val count = reply.readInt().coerceAtLeast(0)
                val out = ArrayList<String>(count)
                repeat(count) { out.add(reply.readString() ?: "") }
                out
            }

        override fun deletePaths(paths: List<String>): ShellResult =
            transact(T_DELETE_PATHS, { it.writeStringList(paths) }) { reply ->
                ShellResult.CREATOR.createFromParcel(reply)
            }

        override fun trimCaches(desiredBytes: Long): TrimResult =
            transact(T_TRIM_CACHES, { it.writeLong(desiredBytes) }) { reply ->
                TrimResult.CREATOR.createFromParcel(reply)
            }

        override fun clearCacheViaBinder(packageName: String): ShellResult =
            transact(T_CLEAR_CACHE_VIA_BINDER, { it.writeString(packageName) }) { reply ->
                ShellResult.CREATOR.createFromParcel(reply)
            }

        override fun queryCacheBytes(packageName: String): Long =
            transact(T_QUERY_CACHE_BYTES, { it.writeString(packageName) }) { reply ->
                reply.readLong()
            }

        override fun destroy() {
            transact(T_DESTROY, {}, { _: Parcel -> Unit })
        }
    }

    companion object {
        const val INTERFACE_TOKEN: String = "io.github.junkmaster.app.shizuku.ICleanerUserService"

        fun asInterface(binder: IBinder?): ICleanerUserService? {
            if (binder == null) return null
            val local = binder.queryLocalInterface(INTERFACE_TOKEN)
            return if (local is ICleanerUserService) local else Proxy(binder)
        }
    }
}
