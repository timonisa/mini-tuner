package com.independentmusictools.tuner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.independentmusictools.tuner.R
import com.independentmusictools.tuner.state.TunerViewModel
import com.independentmusictools.tuner.state.TunerViewModel.Status

/**
 * Главный экран тюнера. Тюнер хроматический: цель — ближайшая нота.
 *
 * Компоновка строится от центра экрана: ось (центр вращения) стрелки
 * всегда в геометрическом центре, остальные блоки позиционируются
 * относительно шкалы.
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Не даём контенту залезать под статус-бар и панель навигации.
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Геометрия шкалы: ось стрелки — точно в центре экрана.
            val gaugeWidth = maxWidth - HORIZONTAL_PADDING * 2
            val gaugeHeight = gaugeWidth / GAUGE_ASPECT
            val gaugeTop = maxHeight / 2 - gaugeHeight * GAUGE_PIVOT_FRACTION

            // Шестерёнка — переход в настройки (верхний правый угол).
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Крупная нота — доминанта экрана.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp),
            ) {
                val noteChar = if (state.status == Status.DETECTED) state.detectedNote else state.targetNote
                Text(
                    text = noteChar,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Состояние: ниже/выше/в строй.
                StatusLine(state)
            }

            // Шкала: опускается ровно настолько, чтобы ось стрелки
            // оказалась в центре экрана.
            NeedleGauge(
                needleCents = state.needleCents,
                rangeCents = TunerViewModel.NEEDLE_RANGE_CENTS,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = gaugeTop)
                    .padding(horizontal = HORIZONTAL_PADDING),
            )

            // Частоты и эталон — ниже шкалы, опускаются вместе с ней.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = gaugeTop + gaugeHeight + BELOW_GAUGE_GAP),
            ) {
                val hzText = state.detectedHz?.let { stringResource(R.string.freq_hz, it) }
                    ?: stringResource(R.string.freq_placeholder)
                Text(
                    text = hzText,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val targetText = state.targetHz?.let {
                    stringResource(R.string.target_note_freq, state.targetNote, it)
                } ?: stringResource(R.string.target_nearest)
                Text(
                    text = targetText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // Эталон A4.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SmallStepButton(
                        symbol = "−",
                        description = stringResource(R.string.a4_lower_cd),
                    ) { viewModel.adjustA4(-1.0) }
                    Text(
                        text = stringResource(R.string.a4_value, state.a4Hz),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SmallStepButton(
                        symbol = "+",
                        description = stringResource(R.string.a4_raise_cd),
                    ) { viewModel.adjustA4(1.0) }
                }
            }

            // Главная кнопка: включить/выключить прослушивание.
            val listening = state.status != Status.IDLE
            val startCd = stringResource(R.string.start_cd)
            val stopCd = stringResource(R.string.stop_cd)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = BOTTOM_MARGIN + MIC_BUTTON_RAISE),
            ) {
                FilledIconButton(
                    onClick = {
                        if (listening) viewModel.stopListening()
                        else permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .semantics {
                            contentDescription = if (listening) stopCd else startCd
                        },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                if (state.permissionDenied) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.MicOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.mic_denied),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(state: TunerViewModel.UiState) {
    val (text, color) = when {
        state.status == Status.IDLE ->
            stringResource(R.string.status_off) to MaterialTheme.colorScheme.onSurfaceVariant
        state.status == Status.LISTENING ->
            stringResource(R.string.status_listening) to MaterialTheme.colorScheme.onSurfaceVariant
        state.status == Status.DETECTED && state.inTune ->
            stringResource(R.string.status_in_tune) to MaterialTheme.colorScheme.primary
        state.status == Status.DETECTED && state.flat == true ->
            stringResource(R.string.status_flat) to MaterialTheme.colorScheme.error
        state.status == Status.DETECTED && state.flat == false ->
            stringResource(R.string.status_sharp) to MaterialTheme.colorScheme.error
        else -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun SmallStepButton(symbol: String, description: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 48.dp)
            .semantics { contentDescription = description },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/** Горизонтальные поля контента от краёв экрана. */
private val HORIZONTAL_PADDING = 24.dp

/** Отступ блока частот от низа шкалы: ~30 dp ≈ 5 мм, чтобы ось и хвост стрелки не задевали текст. */
private val BELOW_GAUGE_GAP = 34.dp

/** Нижний отступ колонки кнопки микрофона до поднятия. */
private val BOTTOM_MARGIN = 24.dp

/** Насколько приподнята кнопка микрофона: 160 dp = 2,54 см → 63 dp ≈ 1 см. */
private val MIC_BUTTON_RAISE = 63.dp
