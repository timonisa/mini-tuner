package com.independentmusictools.tuner.state

import com.independentmusictools.tuner.audio.AudioCapture
import com.independentmusictools.tuner.core.music.PitchMath
import com.independentmusictools.tuner.core.pitch.YinPitchDetector
import com.independentmusictools.tuner.settings.TunerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Состояние и логика тюнера. Аудио-цикл работает в собственном потоке
 * AudioCapture; сюда приходят только готовые буферы.
 */
class TunerViewModel(
    private val settings: TunerSettings,
) {
    /** Что показывать на шкале и в подписи состояния. */
    enum class Status { IDLE, LISTENING, NO_SIGNAL, DETECTED }

    data class UiState(
        val status: Status = Status.IDLE,
        /** Эталон A4, Гц. */
        val a4Hz: Double = PitchMath.A4_HZ,
        /** Обнаруженная частота, Гц (сглаженная); null, если сигнал ненадёжен. */
        val detectedHz: Double? = null,
        /** Ближайшая нота к обнаруженной частоте. */
        val detectedNote: String = "—",
        /** Целевая нота — ближайшая к обнаруженной частоте (хроматический режим). */
        val targetNote: String = "—",
        /** Целевая частота, Гц. */
        val targetHz: Double? = null,
        /** Отклонение в центах для шкалы; null = стрелка в неактивном положении. */
        val needleCents: Double? = null,
        val inTune: Boolean = false,
        val flat: Boolean? = null, // true = ниже, false = выше, null = не определено
        val permissionDenied: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(UiState(a4Hz = settings.a4Hz))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val detector = YinPitchDetector(AudioCapture.SAMPLE_RATE, AudioCapture.BUFFER_SIZE)

    /** Сглаживание: медиана последних частот + экспоненциальное сглаживание центов. */
    private val recentHz = ArrayDeque<Double>(SMOOTH_WINDOW)
    private var smoothedCents: Double? = null
    private var lastGoodAtMs = 0L

    /** Момент (uptime ms), до которого стрелка держится в покое после тишины. */
    private var settleUntilMs = 0L

    private var capture: AudioCapture? = null

    fun onPermissionResult(granted: Boolean) {
        if (granted) startListening() else _state.value = _state.value.copy(permissionDenied = true)
    }

    fun startListening() {
        if (capture?.isRunning == true) return
        _state.value = _state.value.copy(
            status = Status.LISTENING,
            permissionDenied = false,
            needleCents = null,
            detectedHz = null,
            detectedNote = "—",
        )
        recentHz.clear()
        smoothedCents = null
        settleUntilMs = 0L
        capture = AudioCapture { buffer -> onAudioBuffer(buffer) }.also { it.start() }
    }

    fun stopListening() {
        capture?.stop()
        capture = null
        settleUntilMs = 0L
        _state.value = _state.value.copy(
            status = Status.IDLE,
            needleCents = null,
            detectedHz = null,
            detectedNote = "—",
            inTune = false,
            flat = null,
        )
    }

    /** Регулировка эталона A4 в пределах 430–450 Гц. */
    fun adjustA4(deltaHz: Double) {
        val newA4 = (_state.value.a4Hz + deltaHz).coerceIn(430.0, 450.0)
        settings.a4Hz = newA4
        _state.value = _state.value.copy(a4Hz = newA4)
        updateTargets(_state.value.detectedHz)
    }

    private fun onAudioBuffer(buffer: FloatArray) {
        val result = detector.detect(buffer)
        val now = System.currentTimeMillis()
        val s = _state.value

        if (result.frequencyHz == null || result.clarity < MIN_CLARITY) {
            // Ненадёжный сигнал: держим показания полсекунды, затем gracefully гасим.
            if (now - lastGoodAtMs > HOLD_MS && s.status != Status.LISTENING) {
                // Сбрасываем окно и период успокоения: следующее появление
                // сигнала начнётся с чистого листа.
                recentHz.clear()
                smoothedCents = null
                settleUntilMs = 0L
                _state.value = s.copy(
                    status = Status.LISTENING,
                    needleCents = null,
                    detectedHz = null,
                    detectedNote = "—",
                    inTune = false,
                    flat = null,
                )
            }
            return
        }

        // Сигнал появился после тишины: окно с нуля и период успокоения,
        // чтобы стрелка не дёргалась на неустойчивой атаке звука.
        if (now - lastGoodAtMs > HOLD_MS || settleUntilMs == 0L) {
            recentHz.clear()
            smoothedCents = null
            settleUntilMs = now + SETTLE_MS
        }

        val hz = result.frequencyHz
        recentHz.addLast(hz)
        if (recentHz.size > SMOOTH_WINDOW) recentHz.removeFirst()
        val medianHz = median(recentHz)
        lastGoodAtMs = now

        updateTargets(medianHz, now)
    }

    private fun updateTargets(hz: Double?, now: Long = System.currentTimeMillis()) {
        val s = _state.value
        if (hz == null) return

        // Хроматический режим: цель — ближайшая нота к обнаруженной частоте.
        val nearest = PitchMath.nearestNote(hz, s.a4Hz)

        // Экспоненциальное сглаживание центов: стрелка движется спокойно,
        // без дрожания на каждом буфере. Пока идёт период успокоения,
        // стрелка стоит в покое; первый показ — сразу на устоявшейся медиане.
        val settled = now >= settleUntilMs
        val rawCents = nearest.cents.coerceIn(-NEEDLE_RANGE_CENTS, NEEDLE_RANGE_CENTS)
        val smoothed: Double?
        if (settled) {
            smoothed = smoothedCents?.let { prev ->
                // Скачок за один буфер (~46 мс) больше этого — это смена ноты
                // (другая струна или переход через границу полутона), а не
                // движение колка: продолжаем с нового значения, чтобы стрелка
                // не ползла через всю шкалу.
                if (abs(rawCents - prev) > SNAP_CENTS) rawCents
                else SMOOTH_ALPHA * rawCents + (1 - SMOOTH_ALPHA) * prev
            } ?: rawCents
            smoothedCents = smoothed
        } else {
            smoothed = null
        }

        val cents = nearest.cents
        val inTune = smoothed != null &&
            abs(cents) <= IN_TUNE_CENTS && abs(smoothed) <= IN_TUNE_CENTS + 3
        _state.value = s.copy(
            status = Status.DETECTED,
            detectedHz = hz,
            detectedNote = nearest.letter,
            targetNote = nearest.fullName,
            targetHz = nearest.referenceHz,
            needleCents = smoothed,
            inTune = inTune,
            flat = if (abs(cents) <= 0.5) null else cents.sign < 0,
        )
    }

    fun dispose() {
        stopListening()
        scope.cancel()
    }

    companion object {
        /** Окно медианы частот в буферах: 7 ≈ 320 мс, стабилизирует оценку. */
        private const val SMOOTH_WINDOW = 7

        /** Вес новой оценки в сглаживании: меньше — спокойнее стрелка. */
        private const val SMOOTH_ALPHA = 0.18

        /** Пауза после появления сигнала: стрелка стоит, пока оценка не устоится. */
        private const val SETTLE_MS = 500

        /** Скачок центов за один буфер, выше которого считается, что сменилась нота. */
        private const val SNAP_CENTS = 30.0

        private const val MIN_CLARITY = 0.75
        private const val HOLD_MS = 500
        const val IN_TUNE_CENTS = 5.0
        const val NEEDLE_RANGE_CENTS = 25.0

        private fun median(values: Collection<Double>): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }
    }
}
