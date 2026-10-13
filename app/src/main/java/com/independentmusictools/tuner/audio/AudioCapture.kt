package com.independentmusictools.tuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.min

/**
 * Захват звука с микрофона через AudioRecord.
 * Аудио обрабатывается только в памяти и нигде не сохраняется.
 *
 * @param onBuffer вызывается в фоновом потоке; получает отсчёты
 * в диапазоне -1..1 (после усиления [INPUT_GAIN_DB] и ограничения)
 * длиной [bufferSize].
 */
class AudioCapture(
    val sampleRate: Int = SAMPLE_RATE,
    val bufferSize: Int = BUFFER_SIZE,
    private val onBuffer: (FloatArray) -> Unit,
) {
    private var record: AudioRecord? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission") // разрешение проверяется вызывающим кодом
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // без DSP-обработки звонка
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, bufferSize * 2),
            )
        } catch (_: SecurityException) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        record = rec
        running = true
        rec.startRecording()
        thread = Thread(::readLoop, "tuner-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
    }

    private fun readLoop() {
        val rec = record ?: return
        val shorts = ShortArray(bufferSize)
        val floats = FloatArray(bufferSize)
        while (running) {
            val read = rec.read(shorts, 0, bufferSize)
            if (read <= 0) continue
            val n = min(read, bufferSize)
            for (i in 0 until n) {
                floats[i] = (shorts[i] / 32768f * INPUT_GAIN).coerceIn(-1f, 1f)
            }
            if (n == bufferSize) onBuffer(floats.copyOf())
        }
    }

    companion object {
        /** 44,1 кГц: достаточно для всех гитарных частот и доступно на любом устройстве. */
        const val SAMPLE_RATE = 44100

        /**
         * 2048 отсчётов ≈ 46 мс. Период E2 (82,4 Гц) ≈ 535 отсчётов, в буфер
         * помещается почти 4 периода — YIN работает корректно; задержка отклика мала.
         */
        const val BUFFER_SIZE = 2048

        /**
         * Цифровое усиление входа, +12 дБ: тихие струны с большего
         * расстояния увереннее проходят порог тишины детектора.
         * Пики после усиления ограничиваются до -1..1: период сигнала
         * при этом сохраняется, и YIN остаётся устойчив к ограничению.
         */
        const val INPUT_GAIN_DB = 12.0

        /** Линейный множитель для [INPUT_GAIN_DB]: 10^(дБ/20) ≈ ×3,98. */
        private const val INPUT_GAIN = 3.981f
    }
}
