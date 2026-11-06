package io.github.junkmaster.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "junkmaster_prefs")

/**
 * DataStore：用户白名单与「实测 trim-caches」开关。
 * 数据只存本机，不上传、不含文件内容（计划第 3 节 / PRIVACY.md）。
 */
class PreferencesRepository(private val context: Context) {

    companion object {
        val WHITELIST = stringSetPreferencesKey("whitelist")
        val PROBE_TRIM_CACHES = booleanPreferencesKey("probe_trim_caches")

        /** 用户选中的配色方案 id（见 ui/theme/ThemePalette.kt）。只存本机。 */
        val THEME_PALETTE = stringPreferencesKey("theme_palette")

        /** 明暗模式 id：auto / light / dark（见 ui/theme/ThemePalette.kt 的 ThemeMode）。 */
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val whitelist: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[WHITELIST] ?: emptySet() }

    val probeTrimCaches: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[PROBE_TRIM_CACHES] ?: true }

    /** 配色 id 流；空表示用默认色。UI 层再解析成 ThemePalette。 */
    val themePalette: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[THEME_PALETTE] ?: "" }

    suspend fun setThemePalette(id: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_PALETTE] = id
        }
    }

    /** 明暗模式；空表示跟随系统（默认）。 */
    val themeMode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[THEME_MODE] ?: "" }

    suspend fun setThemeMode(id: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = id
        }
    }

    suspend fun addWhitelist(path: String) {
        context.dataStore.edit { prefs ->
            prefs[WHITELIST] = (prefs[WHITELIST] ?: emptySet()) + path
        }
    }

    suspend fun removeWhitelist(path: String) {
        context.dataStore.edit { prefs ->
            prefs[WHITELIST] = (prefs[WHITELIST] ?: emptySet()) - path
        }
    }

    suspend fun setProbeTrimCaches(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PROBE_TRIM_CACHES] = enabled
        }
    }
}
