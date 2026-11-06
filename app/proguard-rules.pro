# =============================================================================
# M5 / 计划第 5 节：R8 回归重点
# UserService 由 Shizuku 通过反射按类名实例化，AIDL Stub / Parcelable / CREATOR
# 一旦被混淆或裁掉，release 包 bindUserService 会失败。必须整体 keep。
# =============================================================================

# ---- 手写 Binder 接口、Stub、Proxy、事务码 ----
-keep interface io.github.junkmaster.app.shizuku.ICleanerUserService { *; }
-keep class io.github.junkmaster.app.shizuku.CleanerServiceStub { *; }
-keep class io.github.junkmaster.app.shizuku.CleanerServiceStub$Proxy { *; }

# ---- Parcelable 数据类 ----
-keep class io.github.junkmaster.app.shizuku.ShellResult { *; }
-keep class io.github.junkmaster.app.shizuku.TrimResult { *; }
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class io.github.junkmaster.app.shizuku.ShellResult { *; }
-keepclassmembers class io.github.junkmaster.app.shizuku.TrimResult { *; }

# ---- UserService（反射实例化，需保留无参构造与类名） ----
-keep class io.github.junkmaster.app.shizuku.CleanerUserService { *; }
-keepclasseswithmembers class io.github.junkmaster.app.shizuku.CleanerUserService {
    public <init>();
}
# 整个 shizuku 包兜底 keep（状态机 / Bridge / Parcelable / Stub 全部保留）
-keep class io.github.junkmaster.app.shizuku.** { *; }
-keep interface io.github.junkmaster.app.shizuku.** { *; }

# ---- Shizuku API 本体 ----
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# ---- 隐藏 API 反射目标（deleteApplicationCacheFiles / StorageStatsManager） ----
-keepclassmembers class android.content.pm.PackageManager {
    public *** deleteApplicationCacheFiles(...);
}
-keepclassmembers class android.app.usage.StorageStatsManager { *; }
-dontwarn android.content.pm.IPackageManager$Stub
-dontwarn android.app.usage.StorageStats

# ---- 通用 ----
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }
