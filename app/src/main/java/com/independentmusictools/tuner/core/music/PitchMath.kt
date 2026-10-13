package com.independentmusictools.tuner.core.music

import kotlin.math.log2

/**
 * Музыкальная математика равномерно-темперированного строя.
 * Чистый Kotlin без зависимостей от Android — покрывается unit-тестами.
 */
object PitchMath {

    /** Стандартный эталон строя A4 = 440 Гц. */
    const val A4_HZ = 440.0

    /** Диапазон регулировки эталона (границы включительно). */
    const val MIN_A4_HZ = 430.0
    const val MAX_A4_HZ = 450.0

    const val SEMITONES_PER_OCTAVE = 12
    const val CENTS_PER_SEMITONE = 100

    private const val SEMITONE_RATIO = 1.0594630943592953 // 2^(1/12)

    /** MIDI-номер A4. */
    const val A4_MIDI = 69

    private val SHARP_NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    /** Частота ноты на [semitonesFromA4] полутонов выше/ниже A4 ([a4Hz]). */
    fun frequency(semitonesFromA4: Int, a4Hz: Double = A4_HZ): Double =
        a4Hz * Math.pow(SEMITONE_RATIO, semitonesFromA4.toDouble())

    /** Частота по MIDI-номеру ноты при эталоне [a4Hz]. */
    fun frequencyByMidi(midi: Int, a4Hz: Double = A4_HZ): Double =
        frequency(midi - A4_MIDI, a4Hz)

    /** Отклонение [actualHz] от [referenceHz] в центах. */
    fun centsOff(actualHz: Double, referenceHz: Double): Double =
        CENTS_PER_SEMITONE * SEMITONES_PER_OCTAVE * log2(actualHz / referenceHz)

    /** MIDI-номер ближайшей к [hz] ноты при эталоне [a4Hz]. */
    fun nearestMidi(hz: Double, a4Hz: Double = A4_HZ): Int {
        val semitones = SEMITONES_PER_OCTAVE * log2(hz / a4Hz)
        return (Math.round(semitones) + A4_MIDI).toInt()
    }

    /** Имя ноты с октавой, например "A4", "C♯3". */
    fun noteName(midi: Int): String {
        val name = SHARP_NAMES[Math.floorMod(midi, 12)]
        val octave = midi / 12 - 1
        return "$name$octave"
    }

    /** Краткое имя ноты без октавы, например "A", "C♯". */
    fun noteLetter(midi: Int): String = SHARP_NAMES[Math.floorMod(midi, 12)]

    /** Ближайшая нота к [hz]: имя и отклонение в центах (-50..+50). */
    fun nearestNote(hz: Double, a4Hz: Double = A4_HZ): NearestNote {
        val midi = nearestMidi(hz, a4Hz)
        val ref = frequencyByMidi(midi, a4Hz)
        val cents = centsOff(hz, ref)
        // Из-за округления cents может чуть выйти за ±50; нормируем.
        val clamped = cents.coerceIn(-50.5, 50.5)
        return NearestNote(midi, noteName(midi), noteLetter(midi), ref, clamped)
    }

    data class NearestNote(
        val midi: Int,
        val fullName: String,
        val letter: String,
        val referenceHz: Double,
        val cents: Double,
    )
}
