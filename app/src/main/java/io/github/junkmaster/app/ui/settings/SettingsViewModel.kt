package io.github.junkmaster.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.junkmaster.app.AppServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 设置页 ViewModel：白名单（deny > allow）与 trim-caches 实测开关。 */
class SettingsViewModel : ViewModel() {

    private val _whitelist = MutableStateFlow<List<String>>(emptyList())
    val whitelist: StateFlow<List<String>> = _whitelist

    private val _probeTrim = MutableStateFlow(true)
    val probeTrim: StateFlow<Boolean> = _probeTrim

    /** 当前配色 id（空 = 使用默认色，UI 用 ThemePalette.fromId 归一化）。 */
    private val _palette = MutableStateFlow("")
    val palette: StateFlow<String> = _palette

    /** 当前明暗模式 id（空 = 跟随系统，UI 用 ThemeMode.fromId 归一化）。 */
    private val _themeMode = MutableStateFlow("")
    val themeMode: StateFlow<String> = _themeMode

    fun load() {
        viewModelScope.launch {
            AppServices.preferences.whitelist.collect { set ->
                _whitelist.value = set.toList().sorted()
                // 同步到内存态 ExclusionRepository（PathGuard 使用）
                val repo = AppServices.exclusions()
                repo.snapshot().forEach { repo.remove(it) }
                set.forEach { repo.add(it) }
            }
        }
        viewModelScope.launch {
            AppServices.preferences.probeTrimCaches.collect { _probeTrim.value = it }
        }
        viewModelScope.launch {
            AppServices.preferences.themePalette.collect { _palette.value = it }
        }
        viewModelScope.launch {
            AppServices.preferences.themeMode.collect { _themeMode.value = it }
        }
    }

    fun setPalette(id: String) {
        viewModelScope.launch {
            AppServices.preferences.setThemePalette(id)
        }
    }

    fun setThemeMode(id: String) {
        viewModelScope.launch {
            AppServices.preferences.setThemeMode(id)
        }
    }

    fun add(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            AppServices.preferences.addWhitelist(path)
            AppServices.exclusions().add(path)
        }
    }

    fun remove(path: String) {
        viewModelScope.launch {
            AppServices.preferences.removeWhitelist(path)
            AppServices.exclusions().remove(path)
        }
    }

    fun setProbeTrim(enabled: Boolean) {
        viewModelScope.launch {
            AppServices.preferences.setProbeTrimCaches(enabled)
        }
    }
}
