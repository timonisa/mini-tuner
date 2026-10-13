package com.independentmusictools.tuner.core.pitch

import kotlin.math.PI
import kotlin.math.sin

/**
 * Генератор тестовых сигналов для unit-тестов и ручной проверки.
 * Чистый Kotlin.
 */
object SignalGenerator {

    fun sine(
        frequencyHz: Double,
        sampleRate: Int,
        size: Int,
        amplitude: Double = 0.5,
        phase: Double = 0.0,
    ): FloatArray = FloatArray(size) { i ->
        (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRate + phase)).toFloat()
    }

    /** Сигнал с гармониками: [harmonicAmplitudes][k] — амплитуда (k+1)-й гармоники. */
    fun harmonic(
        fundamentalHz: Double,
        harmonicAmplitudes: DoubleArray,
        sampleRate: Int,
        size: Int,
    ): FloatArray {
        val amplitudes = harmonicAmplitudes.map { it.toFloat() }.toFloatArray()
        return FloatArray(size) { i ->
            var v = 0.0
            for (k in amplitudes.indices) {
                v += amplitudes[k] * sin(2.0 * PI * fundamentalHz * (k + 1) * i / sampleRate)
            }
            v.toFloat()
        }
    }

    /** Белый шум заданной амплитуды с фиксированным зерном (воспроизводимость). */
    fun noise(seed: Long, amplitude: Double, size: Int): FloatArray {
        var state = seed
        return FloatArray(size) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val unit = ((state ushr 33) / (1L shl 31).toDouble()) - 1.0
            (unit * amplitude).toFloat()
        }
    }
}
