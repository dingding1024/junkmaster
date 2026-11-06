package io.github.junkmaster.app.shizuku

import android.os.Parcel
import android.os.Parcelable

/** AIDL Parcelable：trim-caches 实测结果与回退策略标记。 */
data class TrimResult(
    val ok: Boolean = false,
    val rawOutput: String = "",
    /** 实际释放量：按实测记账，绝不谎报 */
    val freedBytes: Long = 0L,
    val strategy: String = ""
) : Parcelable {

    constructor(parcel: Parcel) : this(
        ok = parcel.readInt() != 0,
        rawOutput = parcel.readString() ?: "",
        freedBytes = parcel.readLong(),
        strategy = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(if (ok) 1 else 0)
        parcel.writeString(rawOutput)
        parcel.writeLong(freedBytes)
        parcel.writeString(strategy)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TrimResult> = object : Parcelable.Creator<TrimResult> {
            override fun createFromParcel(parcel: Parcel): TrimResult = TrimResult(parcel)
            override fun newArray(size: Int): Array<TrimResult?> = arrayOfNulls(size)
        }
    }
}
