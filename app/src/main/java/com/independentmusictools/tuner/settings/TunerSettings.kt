package com.independentmusictools.tuner.settings

import android.content.Context
import android.content.SharedPreferences

/** Тема оформления. */
enum class ThemeMode {
    LIGHT,
    DARK;

    companion object {
        fun from(raw: String?): ThemeMode = if (raw == DARK.name) DARK else LIGHT
    }
}

/**
 * Локальные настройки приложения (SharedPreferences, только это устройство).
 * Язык интерфейса по умолчанию — русский, независимо от языка системы.
 */
class TunerSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE)

    /** Эталонный строй A4, Гц (430–450). */
    var a4Hz: Double
        get() = prefs.getFloat(KEY_A4, DEFAULT_A4.toFloat()).toDouble()
        set(value) {
            val clamped = value.coerceIn(430.0, 450.0)
            prefs.edit().putFloat(KEY_A4, clamped.toFloat()).apply()
        }

    /** Тема оформления. */
    var themeMode: ThemeMode
        get() = ThemeMode.from(prefs.getString(KEY_THEME, null))
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    /** Язык интерфейса: ISO-код ("ru", "en", "es", "de", "zh"). */
    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, null) ?: DEFAULT_LANGUAGE
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    companion object {
        private const val KEY_A4 = "a4_hz"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language_tag"
        const val DEFAULT_A4 = 440.0
        const val DEFAULT_LANGUAGE = "ru"
    }
}
