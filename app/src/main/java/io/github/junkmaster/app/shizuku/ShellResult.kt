package io.github.junkmaster.app.shizuku

import android.os.Parcel
import android.os.Parcelable

/** AIDL Parcelable：UserService 通用结果。CREATOR 必须保留（proguard-rules.pro 已 keep）。 */
data class ShellResult(
    val ok: Boolean = false,
    val message: String = "",
    val deletedCount: Int = 0,
    val freedBytes: Long = 0L,
    /** 命令 / 探测的原始输出，用于如实报告 trim-caches 实测结论 */
    val rawOutput: String = ""
) : Parcelable {

    constructor(parcel: Parcel) : this(
        ok = parcel.readInt() != 0,
        message = parcel.readString() ?: "",
        deletedCount = parcel.readInt(),
        freedBytes = parcel.readLong(),
        rawOutput = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(if (ok) 1 else 0)
        parcel.writeString(message)
        parcel.writeInt(deletedCount)
        parcel.writeLong(freedBytes)
        parcel.writeString(rawOutput)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ShellResult> = object : Parcelable.Creator<ShellResult> {
            override fun createFromParcel(parcel: Parcel): ShellResult = ShellResult(parcel)
            override fun newArray(size: Int): Array<ShellResult?> = arrayOfNulls(size)
        }
    }
}
