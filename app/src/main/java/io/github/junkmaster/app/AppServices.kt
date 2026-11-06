package io.github.junkmaster.app

import android.content.Context
import io.github.junkmaster.app.clean.CleanExecutor
import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.clean.DeleteJournal
import io.github.junkmaster.app.clean.JvmCapacityProbe
import io.github.junkmaster.app.clean.LastCleanSummary
import io.github.junkmaster.app.clean.NioFileDeleter
import io.github.junkmaster.app.clean.StorageRoots
import io.github.junkmaster.app.data.PreferencesRepository
import io.github.junkmaster.app.diag.LastScanSummary
import io.github.junkmaster.app.scan.BasicCacheSource
import io.github.junkmaster.app.scan.CorpseFinder
import io.github.junkmaster.app.scan.DownloadScanner
import io.github.junkmaster.app.scan.EmptyDirFinder
import io.github.junkmaster.app.scan.EntriesProvider
import io.github.junkmaster.app.scan.ExclusionRepository
import io.github.junkmaster.app.scan.FileEntry
import io.github.junkmaster.app.scan.FileSystemWalker
import io.github.junkmaster.app.scan.ItemSource
import io.github.junkmaster.app.scan.LargeFileScanner
import io.github.junkmaster.app.scan.LogScanner
import io.github.junkmaster.app.scan.MediaCacheScanner
import io.github.junkmaster.app.scan.MediaStoreScanner
import io.github.junkmaster.app.scan.PackageRepository
import io.github.junkmaster.app.scan.ScanResult
import io.github.junkmaster.app.scan.ScanSources
import io.github.junkmaster.app.scan.TempFileSource
import io.github.junkmaster.app.scan.ThumbnailScanner
import io.github.junkmaster.app.shizuku.BridgeFileDeleter
import io.github.junkmaster.app.shizuku.ShizukuCacheItemSource
import io.github.junkmaster.app.shizuku.ShizukuGateway
import io.github.junkmaster.app.shizuku.ShizukuStatus
import java.io.File

/**
 * 轻量服务定位（单 Activity 应用）。避免引入 Hilt / Koin 依赖。
 * MainActivity.onCreate 中先调用 init()。
 */
object AppServices {

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val c = appContext ?: return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 崩溃栈落盘到私有目录，供「测试诊断」页读取并脱敏后由用户主动分享。
            runCatching {
                val dir = File(c.filesDir, "crash").apply { mkdirs() }
                File(dir, "latest.txt").writeText(
                    buildString {
                        append("ts=").append(System.currentTimeMillis()).append('\n')
                        append("thread=").append(thread.name).append('\n')
                        append("version=").append(BuildConfig.VERSION_NAME)
                            .append('(').append(BuildConfig.VERSION_CODE).append(")\n")
                        append("model=").append(android.os.Build.MODEL)
                            .append(" sdk=").append(android.os.Build.VERSION.SDK_INT).append('\n')
                        append(throwable.stackTraceToString())
                    },
                    Charsets.UTF_8
                )
            }
            // 必须 delegate 原 handler，否则崩溃后进程卡死且不产生系统崩溃日志。
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun ctx(): Context = appContext
        ?: throw IllegalStateException("AppServices 未初始化，请先在主 Activity 调用 init()")

    /** 诊断页等需要的 Application Context（不持有 Activity，避免泄漏）。 */
    fun applicationContext(): Context = ctx()

    private val exclusions = ExclusionRepository()

    /**
     * Shizuku 网关。
     *
     * versionCode 与 debuggable 必须显式传入 UserServiceArgs：
     *  - version 决定 App 升级后是否强制重启 UserService 进程（否则会复用旧 dex）；
     *  - debuggable 必须与 APK 实际值一致，否则 Shizuku 拒绝加载 UserService。
     */
    val shizuku: ShizukuGateway by lazy {
        ShizukuGateway(
            appPackageName = ctx().packageName,
            appVersionCode = BuildConfig.VERSION_CODE,
            debuggable = BuildConfig.DEBUG
        )
    }
    val preferences: PreferencesRepository by lazy { PreferencesRepository(ctx()) }

    fun exclusions(): ExclusionRepository = exclusions

    private fun journal(): DeleteJournal =
        DeleteJournal(File(ctx().filesDir, "clean_logs"))

    /** 诊断页：清理日志文件列表（私有目录，只有本 App 可读）。 */
    fun journalFiles(): List<File> = journal().files()

    /** 诊断页：最近一次崩溃栈文件。 */
    fun crashFile(): File = File(ctx().filesDir, "crash/latest.txt")

    /** 诊断页：最近一次清理报告摘要（纯 key=value，无路径）。 */
    fun lastCleanFile(): File = File(ctx().filesDir, "diag/" + LastCleanSummary.FILE_NAME)

    /** 每次清理结束后落盘摘要，供诊断报告自证「为什么是 0」。 */
    fun writeLastClean(report: CleanReport) {
        runCatching {
            LastCleanSummary.write(
                lastCleanFile(),
                LastCleanSummary.render(report, System.currentTimeMillis())
            )
        }
    }

    /** 诊断页：最近一次扫描分布摘要（纯 key=value，只有计数与 ruleId，无路径）。 */
    fun lastScanFile(): File = File(ctx().filesDir, "diag/" + LastScanSummary.FILE_NAME)

    /** 每次只读扫描结束后落盘分布，供诊断报告自证「扫描到底产出了什么」。 */
    fun writeLastScan(result: ScanResult) {
        runCatching {
            LastScanSummary.write(
                lastScanFile(),
                LastScanSummary.render(result, System.currentTimeMillis())
            )
        }
    }

    /** 容量探测应指向共享存储根，而不是根分区 `/`（Android 上是只读 ramdisk）。 */
    fun storageRoot(): String = try {
        android.os.Environment.getExternalStorageDirectory().absolutePath
    } catch (t: Throwable) {
        StorageRoots.DEFAULT_STORAGE_PATH
    }

    /** 基础 L1：App 进程内 NIO 直删（不依赖 Shizuku）。 */
    fun cleanExecutor(): CleanExecutor = CleanExecutor(
        deleter = NioFileDeleter(),
        guardContext = exclusions.guardContext(),
        journal = journal(),
        capacityProbe = JvmCapacityProbe()
    )

    /** 深度 L2：删除动作经 Shizuku UserService（shell UID）执行，仍受 CleanExecutor 约束。 */
    fun deepCleanExecutor(): CleanExecutor = CleanExecutor(
        deleter = BridgeFileDeleter(shizuku),
        guardContext = exclusions.guardContext(),
        journal = journal(),
        capacityProbe = JvmCapacityProbe()
    )

    /** 是否已解锁深度清理（Shizuku READY）。基础模式不依赖此状态。 */
    fun shizukuReady(): Boolean = shizuku.currentStatus() == ShizukuStatus.READY

    /**
     * 基础模式所需权限（与 Shizuku 无关）：
     * Pair(所有文件访问权, 用量访问权)。首页据此引导授权，未授权时基础扫描会拿到空结果。
     */
    fun basePermissions(): Pair<Boolean, Boolean> {
        val c = ctx()
        val allFiles = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
        val appOps = c.getSystemService(android.content.Context.APP_OPS_SERVICE)
                as? android.app.AppOpsManager
        val usage = appOps?.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            c.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
        return Pair(allFiles, usage)
    }

    /**
     * 组装只读 dry-run 扫描来源（双模式，老板补充要求）：
     *  - base：App 进程可读的共享存储垃圾，**不开 Shizuku 也照常可用**；
     *  - privileged：/sdcard/Android/data 应用缓存，只由 shell UID 的 UserService 列举，
     *    仅在 Shizuku READY 时加入，打开 Shizuku 即解锁类似 SD Maid 的深度清理。
     * 调用方需在 IO 线程执行（会遍历文件系统）。
     */
    fun sources(): List<ItemSource> {
        val walker = FileSystemWalker()
        // 第四轮修正：/sdcard 与 /storage/emulated/0 是同一个目录的两个别名，
        // 旧版两个根各遍历一次会产出两套路径前缀完全相同内容的 FileEntry，
        // distinctBy { it.path } 无法去重（前缀不同），导致同一项被枚举两次。
        // 统一走 Environment 返回的共享存储根，只遍历一次。
        val snapshot: List<FileEntry> = walker.walk(storageRoot())
        val provider: EntriesProvider = { snapshot }
        val installed: () -> Set<String> = { PackageRepository(ctx()).installedPackages() }
        val base: List<ItemSource> = listOf(
            TempFileSource(provider),
            BasicCacheSource(provider),
            ThumbnailScanner(provider),
            EmptyDirFinder(provider),
            LogScanner(provider),
            MediaCacheScanner(provider),
            DownloadScanner(provider),
            CorpseFinder(provider, installed),
            MediaStoreScanner(ctx()),
            // 第七轮反馈第 4 条：超过 1 个月的大文件索引（只索引、给出路径，永不删除）。
            // 复用上面同一份快照，不额外遍历文件系统。
            LargeFileScanner(provider)
        )
        // AppCacheScanner 已废弃：普通 App 进程列不出 /sdcard/Android/data，
        // 改由 ShizukuCacheItemSource 在 shell 进程里枚举。
        val deep: ItemSource? = ShizukuCacheItemSource(shizuku)
        return ScanSources.build(base, deep, shizukuReady())
    }
}
