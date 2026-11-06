package org.osmwatch.changesetwatch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferenze di aspetto (stesso schema delle altre app: tema, palette, lingua). Reattive; stesso file di [Prefs]. */
object AppSettings {
    enum class Theme { SYSTEM, LIGHT, DARK }
    enum class Palette { BRAND, DYNAMIC }

    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(Theme.SYSTEM)
    val theme: StateFlow<Theme> = _theme
    private val _palette = MutableStateFlow(Palette.BRAND)
    val palette: StateFlow<Palette> = _palette
    private val _lang = MutableStateFlow("auto")
    val lang: StateFlow<String> = _lang

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = enumOr(prefs.getString("theme", null), Theme.SYSTEM)
        _palette.value = enumOr(prefs.getString("palette", null), Palette.BRAND)
        _lang.value = prefs.getString("lang", "auto") ?: "auto"
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E) =
        runCatching { enumValueOf<E>(name!!) }.getOrDefault(default)

    fun setTheme(v: Theme) { _theme.value = v; prefs.edit().putString("theme", v.name).apply() }
    fun setPalette(v: Palette) { _palette.value = v; prefs.edit().putString("palette", v.name).apply() }
    fun setLang(v: String) { _lang.value = v; prefs.edit().putString("lang", v).apply() }
}
