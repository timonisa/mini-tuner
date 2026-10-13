package com.independentmusictools.tuner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.independentmusictools.tuner.state.TunerViewModel
import com.independentmusictools.tuner.state.TunerViewModel.Status
import com.independentmusictools.tuner.ui.theme.CharcoalMuted

/**
 * Главный экран тюнера. Тюнер хроматический: цель — ближайшая нота.
 *
 * Компоновка строится от центра экрана: ось (центр вращения) стрелки
 * шкалы всегда в геометрическом центре, остальные блоки позиционируются
 * относительно шкалы. Заголовок — по середине левой половины экрана.
 */
@Composable
fun TunerScreen(viewModel: TunerViewModel) {
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Геометрия шкалы: ось стрелки — точно в центре экрана.
            val gaugeWidth = maxWidth - HORIZONTAL_PADDING * 2
            val gaugeHeight = gaugeWidth / GAUGE_ASPECT
            val gaugeTop = maxHeight / 2 - gaugeHeight * GAUGE_PIVOT_FRACTION

            // Заголовок: середина левой половины экрана, сверху.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "ТЮНЕР",
                        style = MaterialTheme.typography.labelSmall,
                        color = CharcoalMuted,
                        modifier = Modifier.semantics { contentDescription = "Гитарный тюнер" },
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            // Крупная нота — доминанта экрана.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
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

            // Частоты и эталон — сразу под шкалой, опускаются вместе с ней.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = gaugeTop + gaugeHeight + 4.dp),
            ) {
                val hzText = state.detectedHz?.let { String.format("%.1f Гц", it) } ?: "— Гц"
                Text(
                    text = hzText,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val targetText = state.targetHz?.let {
                    String.format("цель %s · %.1f Гц", state.targetNote, it)
                } ?: "цель — ближайшая нота"
                Text(
                    text = targetText,
                    fontSize = 13.sp,
                    color = CharcoalMuted,
                )

                Spacer(Modifier.height(12.dp))

                // Эталон A4.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SmallStepButton("−", "Понизить эталон A4") { viewModel.adjustA4(-1.0) }
                    Text(
                        text = String.format("A4 = %.0f Гц", state.a4Hz),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SmallStepButton("+", "Повысить эталон A4") { viewModel.adjustA4(1.0) }
                }
            }

            // Главная кнопка: включить/выключить прослушивание.
            val listening = state.status != Status.IDLE
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
                            contentDescription = if (listening) "Остановить тюнер" else "Включить тюнер"
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
                            text = "Без доступа к микрофону тюнер не работает.\nРазрешите доступ в настройках системы.",
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
        state.status == Status.IDLE -> "выключен" to CharcoalMuted
        state.status == Status.LISTENING -> "слушаю…" to CharcoalMuted
        state.status == Status.DETECTED && state.inTune -> "В СТРОЙ" to MaterialTheme.colorScheme.primary
        state.status == Status.DETECTED && state.flat == true -> "ниже — подтяните" to MaterialTheme.colorScheme.error
        state.status == Status.DETECTED && state.flat == false -> "выше — ослабьте" to MaterialTheme.colorScheme.error
        else -> "…" to CharcoalMuted
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

/** Нижний отступ колонки кнопки микрофона до поднятия. */
private val BOTTOM_MARGIN = 24.dp

/** Насколько приподнята кнопка микрофона: 160 dp = 2,54 см → 63 dp ≈ 1 см. */
private val MIC_BUTTON_RAISE = 63.dp
