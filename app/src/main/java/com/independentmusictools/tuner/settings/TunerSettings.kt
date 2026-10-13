package com.independentmusictools.tuner.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Локальные настройки тюнера (SharedPreferences, только это устройство).
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

    companion object {
        private const val KEY_A4 = "a4_hz"
        const val DEFAULT_A4 = 440.0
    }
}
