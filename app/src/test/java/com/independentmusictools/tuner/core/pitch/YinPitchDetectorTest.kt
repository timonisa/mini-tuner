package com.independentmusictools.tuner.core.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты YIN-детектора на синтетических сигналах.
 * Допуск по частоте ~1 % (≈17 центов) — консервативная граница
 * для буфера 2048 отсчёта при 44,1 кГц с параболической интерполяцией.
 */
class YinPitchDetectorTest {

    private val sampleRate = 44100
    private val bufferSize = 2048
    private val detector = YinPitchDetector(sampleRate, bufferSize)

    private fun relativeHzError(detected: Double, expected: Double) =
        Math.abs(detected - expected) / expected

    @Test
    fun `detects 440 Hz sine`() {
        val r = detector.detect(SignalGenerator.sine(440.0, sampleRate, bufferSize))
        assertNotNull(r.frequencyHz)
        assertTrue(relativeHzError(r.frequencyHz!!, 440.0) < 0.01)
    }

    @Test
    fun `detects low E2 around 82 hertz`() {
        val r = detector.detect(SignalGenerator.sine(82.41, sampleRate, bufferSize))
        assertNotNull(r.frequencyHz)
        assertTrue("got ${r.frequencyHz}", relativeHzError(r.frequencyHz!!, 82.41) < 0.01)
    }

    @Test
    fun `detects all six open guitar strings`() {
        val strings = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
        for (expected in strings) {
            val r = detector.detect(SignalGenerator.sine(expected, sampleRate, bufferSize))
            assertNotNull("не обнаружена $expected Гц", r.frequencyHz)
            assertTrue(
                "$expected Гц → ${r.frequencyHz} Гц",
                relativeHzError(r.frequencyHz!!, expected) < 0.01,
            )
        }
    }

    @Test
    fun `silence produces no detection`() {
        val r = detector.detect(FloatArray(bufferSize))
        assertNull(r.frequencyHz)
    }

    @Test
    fun `very low amplitude is rejected`() {
        val r = detector.detect(SignalGenerator.sine(220.0, sampleRate, bufferSize, amplitude = 0.0005))
        assertNull(r.frequencyHz)
    }

    @Test
    fun `quiet but audible signal is still detected`() {
        val r = detector.detect(SignalGenerator.sine(220.0, sampleRate, bufferSize, amplitude = 0.01))
        assertNotNull(r.frequencyHz)
        assertTrue(relativeHzError(r.frequencyHz!!, 220.0) < 0.01)
    }

    @Test
    fun `harmonic-rich signal does not produce octave errors`() {
        // Вторая и третья гармоники сильнее основной — типично для гитары.
        val amplitudes = doubleArrayOf(0.20, 0.50, 0.30, 0.15)
        for (f0 in doubleArrayOf(110.0, 146.83, 196.0)) {
            val r = detector.detect(SignalGenerator.harmonic(f0, amplitudes, sampleRate, bufferSize))
            assertNotNull("гармонический сигнал $f0 Гц не распознан", r.frequencyHz)
            assertTrue(
                "октавная ошибка для $f0 Гц: ${r.frequencyHz}",
                relativeHzError(r.frequencyHz!!, f0) < 0.01,
            )
        }
    }

    @Test
    fun `noisy signal is detected`() {
        val signal = SignalGenerator.sine(196.0, sampleRate, bufferSize, amplitude = 0.4)
        val noise = SignalGenerator.noise(seed = 42L, amplitude = 0.05, size = bufferSize)
        val mixed = FloatArray(bufferSize) { signal[it] + noise[it] }
        val r = detector.detect(mixed)
        assertNotNull(r.frequencyHz)
        assertTrue(relativeHzError(r.frequencyHz!!, 196.0) < 0.01)
    }

    @Test
    fun `out-of-range frequencies are rejected`() {
        // Ниже MIN_DETECT_HZ (60) — инфразвук для тюнера бесполезен
        val tooLow = detector.detect(SignalGenerator.sine(40.0, sampleRate, bufferSize))
        assertNull(tooLow.frequencyHz)
    }

    @Test
    fun `clarity of a clean sine is high`() {
        val r = detector.detect(SignalGenerator.sine(110.0, sampleRate, bufferSize))
        assertTrue("clarity = ${r.clarity}", r.clarity > 0.9)
    }
}
