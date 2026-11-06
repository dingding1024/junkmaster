package io.github.junkmaster.app.ui.about

import androidx.lifecycle.ViewModel
import io.github.junkmaster.app.BuildConfig

/** 关于页 ViewModel（版本号与静态合规信息）。 */
class AboutViewModel : ViewModel() {
    val version: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}
