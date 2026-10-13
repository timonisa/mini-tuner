package com.independentmusictools.tuner.core.pitch

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Детектор основной частоты по алгоритму YIN
 * (de Cheveigné, Kawahara, 2002). Чистый Kotlin, без Android-зависимостей,
 * покрывается unit-тестами на синтетических сигналах.
 *
 * Этапы: разностная функция → кумулятивная нормализация →
 * поиск первого минимума ниже порога → параболическая интерполяция.
 * Подавление октавных ошибок вверх обеспечивается выбором ПЕРВОГО
 * (наименьшего лага) минимума ниже порога; вниз — ограничением tauMax.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val bufferSize: Int,
    private val threshold: Double = 0.15,
) {
    init {
        require(bufferSize > 0) { "bufferSize должен быть положительным" }
    }

    /** Максимальный период (лаг) в выборках; детектируемые частоты ≥ sampleRate/tauMax. */
    private val tauMax: Int = min(bufferSize / 2, 1200)

    data class Result(
        val frequencyHz: Double?,
        /** Ясность 0..1 (1 − значение нормализованной разностной функции в минимуме). */
        val clarity: Double,
        /** Среднеквадратичная амплитуда буфера. */
        val rms: Double,
    )

    /**
     * @param samples PCM-отсчёты в диапазоне примерно -1..1
     */
    fun detect(samples: FloatArray): Result {
        require(samples.size >= bufferSize) {
            "нужно ≥ $bufferSize отсчётов, получено ${samples.size}"
        }
        val buffer = if (samples.size == bufferSize) samples else samples.copyOf(bufferSize)
        val rms = rms(buffer)
        if (rms < SILENCE_RMS) return Result(null, 0.0, rms)

        val difference = differenceFunction(buffer)
        val normalized = cumulativeMeanNormalized(difference)
        val tauEstimate = findTau(normalized)
        if (tauEstimate < 0) return Result(null, 0.0, rms)

        val betterTau = parabolicInterpolation(normalized, tauEstimate)
        val frequency = sampleRate / betterTau
        if (frequency < MIN_DETECT_HZ || frequency > MAX_DETECT_HZ) {
            return Result(null, 0.0, rms)
        }
        val clarity = (1.0 - normalized[tauEstimate]).coerceIn(0.0, 1.0)
        return Result(frequency, clarity, rms)
    }

    private fun rms(buffer: FloatArray): Double {
        var sum = 0.0
        for (s in buffer) sum += (s * s).toDouble()
        return Math.sqrt(sum / buffer.size)
    }

    /** Шаг 1: разностная функция d(tau) (O(n·tauMax), приемлемо для буфера 2048). */
    private fun differenceFunction(buffer: FloatArray): DoubleArray {
        val d = DoubleArray(tauMax)
        for (tau in 1 until tauMax) {
            var sum = 0.0
            for (i in 0 until tauMax) {
                val delta = buffer[i] - buffer[i + tau]
                sum += delta * delta
            }
            d[tau] = sum
        }
        return d
    }

    /** Шаг 2: кумулятивная средняя нормализованная разностная функция. */
    private fun cumulativeMeanNormalized(d: DoubleArray): DoubleArray {
        val n = DoubleArray(tauMax)
        n[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until tauMax) {
            runningSum += d[tau]
            n[tau] = if (runningSum != 0.0) d[tau] * tau / runningSum else 1.0
        }
        return n
    }

    /**
     * Шаг 3: абсолютный порог — первый локальный минимум ниже [threshold].
     * Именно «первый» минимум исключает октавные ошибки вниз
     * (период × 2 даёт минимум позже).
     */
    private fun findTau(n: DoubleArray): Int {
        var tau = 2
        while (tau < tauMax - 1) {
            if (n[tau] < threshold) {
                // доходим до локального минимума
                while (tau + 1 < tauMax - 1 && n[tau + 1] < n[tau]) tau++
                return tau
            }
            tau++
        }
        // Порог не достигнут — берём глобальный минимум, но пометим низкой ясностью.
        var bestTau = -1
        var bestVal = Double.MAX_VALUE
        for (t in 2 until tauMax - 1) {
            if (n[t] < bestVal) {
                bestVal = n[t]
                bestTau = t
            }
        }
        return if (bestVal < FALLBACK_THRESHOLD) bestTau else -1
    }

    /** Шаг 4: параболическая интерполяция вокруг [tau] для субвыборочной точности. */
    private fun parabolicInterpolation(n: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= n.size - 1) return tau.toDouble()
        val s0 = n[tau - 1]
        val s1 = n[tau]
        val s2 = n[tau + 1]
        val denom = 2.0 * (2.0 * s1 - s2 - s0)
        if (denom == 0.0) return tau.toDouble()
        val shift = (s2 - s0) / denom
        // Ограничиваем сдвиг внутри соседних лагов.
        return tau + max(-1.0, min(1.0, shift))
    }

    companion object {
        /** Тишина: RMS ниже этого уровня не обрабатывается вовсе. */
        const val SILENCE_RMS = 0.002

        /** Если порог не достигнут, принимаем минимум только если он достаточно глубок. */
        const val FALLBACK_THRESHOLD = 0.35

        const val MIN_DETECT_HZ = 60.0
        const val MAX_DETECT_HZ = 1400.0
    }
}
