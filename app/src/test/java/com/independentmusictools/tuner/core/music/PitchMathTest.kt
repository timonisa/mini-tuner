package com.independentmusictools.tuner.core.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchMathTest {

    @Test
    fun `A4 frequency is exactly 440`() {
        assertEquals(440.0, PitchMath.frequency(0), 1e-9)
    }

    @Test
    fun `one octave above A4 doubles frequency`() {
        assertEquals(880.0, PitchMath.frequency(12), 0.01)
    }

    @Test
    fun `standard tuning frequencies match known values`() {
        // E2 ≈ 82.41, A2 = 110.00, D3 ≈ 146.83, G3 = 196.00, B3 ≈ 246.94, E4 ≈ 329.63
        // Аргумент — полутоны от A4 (E2 = -29, A2 = -24, D3 = -19, G3 = -14, B3 = -10, E4 = -5).
        assertEquals(82.407, PitchMath.frequency(-29), 0.01)
        assertEquals(110.000, PitchMath.frequency(-24), 0.01)
        assertEquals(146.832, PitchMath.frequency(-19), 0.01)
        assertEquals(196.000, PitchMath.frequency(-14), 0.01)
        assertEquals(246.942, PitchMath.frequency(-10), 0.01)
        assertEquals(329.628, PitchMath.frequency(-5), 0.01)
    }

    @Test
    fun `reference pitch change updates frequencies`() {
        val e2at440 = PitchMath.frequency(-29, 440.0)
        val e2at442 = PitchMath.frequency(-29, 442.0)
        assertEquals(true, e2at442 > e2at440)
        // 2 Гц на A4 ≈ 7.85 цента на любой ноте
        val cents = PitchMath.centsOff(e2at442, e2at440)
        assertEquals(7.85, cents, 0.1)
    }

    @Test
    fun `equal reference means zero cents`() {
        assertEquals(0.0, PitchMath.centsOff(440.0, 440.0), 1e-9)
    }

    @Test
    fun `one semitone flat is minus one hundred cents`() {
        assertEquals(-100.0, PitchMath.centsOff(415.3, 440.0), 0.1)
    }

    @Test
    fun `nearest note detects A4 from 440`() {
        val n = PitchMath.nearestNote(440.0)
        assertEquals("A4", n.fullName)
        assertEquals(0.0, n.cents, 0.01)
    }

    @Test
    fun `nearest note detects slightly sharp E4`() {
        val n = PitchMath.nearestNote(331.0) // чуть выше E4 (329.63)
        assertEquals("E4", n.fullName)
        assertEquals(true, n.cents in 1.0..10.0)
    }

    @Test
    fun `nearest note snaps to closer neighbour across half-semitone`() {
        // Середина между A4 (440) и A♯4 (466.16): ближе к A♯
        val n = PitchMath.nearestNote(455.0)
        assertEquals("A♯4", n.fullName)
        assertEquals(true, n.cents < 0)
    }

    @Test
    fun `nearest note honours custom reference pitch`() {
        // При A4=442 частота 440 звучит ниже A4 примерно на 8 центов
        val n = PitchMath.nearestNote(440.0, a4Hz = 442.0)
        assertEquals("A4", n.fullName)
        assertEquals(-7.85, n.cents, 0.2)
    }

    @Test
    fun `low E2 string is recognised`() {
        val n = PitchMath.nearestNote(82.41)
        assertEquals("E2", n.fullName)
    }

    @Test
    fun `note names are correct around octave boundary`() {
        assertEquals("B3", PitchMath.noteName(PitchMath.A4_MIDI - 10))
        assertEquals("C4", PitchMath.noteName(PitchMath.A4_MIDI - 9))
        assertEquals("E4", PitchMath.noteName(PitchMath.A4_MIDI - 5))
    }
}
