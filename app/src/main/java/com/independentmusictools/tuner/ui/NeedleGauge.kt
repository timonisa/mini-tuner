package com.independentmusictools.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Полукруглая аналоговая шкала тюнера (диапазон ±[rangeCents] центов).
 * Рисуется программно через Canvas; стрелка — геометрия, не изображение.
 * Цвета берутся из темы Material.
 *
 * Система углов Canvas: 0° = 3 часа, рост по часовой. Верхняя полуокружность
 * занимает 180°–360°, зенит (центр шкалы) = 270°.
 */
@Composable
fun NeedleGauge(
    needleCents: Double?,
    rangeCents: Double,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val tickStyle = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GAUGE_ASPECT),
    ) {
        val centerX = size.width / 2f
        val centerY = size.height * GAUGE_PIVOT_FRACTION
        val radius = minOf(centerX * 0.94f, centerY * 0.94f)
        val arcSize = Size(radius * 2f, radius * 2f)
        val arcTopLeft = Offset(centerX - radius, centerY - radius)

        // Дуга шкалы
        drawArc(
            color = scheme.outline,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Зона «в строе» ±5 центов у зенита
        val tuneHalfDeg = (IN_TUNE_ZONE_CENTS / rangeCents * 90.0).toFloat()
        drawArc(
            color = scheme.tertiary.copy(alpha = 0.9f),
            startAngle = 270f - tuneHalfDeg,
            sweepAngle = 2f * tuneHalfDeg,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 4.dp.toPx()),
        )

        // Деления: каждые 5 центов мелкие, каждые 25 — крупные с подписью
        for (cents in -rangeCents.toInt()..rangeCents.toInt() step 5) {
            val angleDeg = arcAngleFor(cents.toDouble(), rangeCents)
            val isMajor = cents % 25 == 0
            val isCenter = cents == 0
            val tickInner = radius - when {
                isCenter -> radius * 0.13f
                isMajor -> radius * 0.09f
                else -> radius * 0.045f
            }
            drawLine(
                color = when {
                    isCenter -> scheme.tertiary
                    isMajor -> scheme.onBackground
                    else -> scheme.outline
                },
                start = pointAt(centerX, centerY, tickInner, angleDeg),
                end = pointAt(centerX, centerY, radius, angleDeg),
                strokeWidth = if (isMajor || isCenter) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (isMajor) {
                val label = when {
                    cents == 0 -> "0"
                    cents < 0 -> cents.toString()
                    else -> "+$cents"
                }
                val layout = textMeasurer.measure(label, tickStyle)
                val pos = pointAt(centerX, centerY, radius - radius * 0.22f, angleDeg)
                drawText(
                    layout,
                    topLeft = Offset(
                        pos.x - layout.size.width / 2f,
                        pos.y - layout.size.height / 2f,
                    ),
                )
            }
        }

        // Стрелка: в покое (needleCents == null) лежит на нуле приглушённым цветом
        val needleDeg = arcAngleFor(needleCents ?: 0.0, rangeCents)
        val active = needleCents != null
        rotate(degrees = needleDeg - 180f, pivot = Offset(centerX, centerY)) {
            // Стрелка нарисована направленной влево (180°), поворот ставит её на нужный угол
            drawLine(
                color = if (active) scheme.tertiary else scheme.tertiary.copy(alpha = 0.30f),
                start = Offset(centerX, centerY),
                end = Offset(centerX - radius * 0.90f, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine( // противовес
                color = scheme.onBackground,
                start = Offset(centerX, centerY),
                end = Offset(centerX + radius * 0.14f, centerY),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = scheme.onBackground, radius = 5.dp.toPx(), center = Offset(centerX, centerY))
        drawCircle(color = scheme.background, radius = 2.dp.toPx(), center = Offset(centerX, centerY))
    }
}

/** Угол на дуге (в градусах, система Canvas) для отклонения в центах. */
private fun arcAngleFor(cents: Double, rangeCents: Double): Float {
    val fraction = (cents / rangeCents + 1.0) / 2.0 // 0..1, 0.5 = в строй
    return (180f + fraction.toFloat() * 180f)
}

private fun pointAt(cx: Float, cy: Float, r: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat())
}

/** Отношение ширины холста шкалы к его высоте. */
const val GAUGE_ASPECT = 1.55f

/** Доля высоты холста, на которой лежит ось (центр вращения) стрелки. */
const val GAUGE_PIVOT_FRACTION = 0.94f

private const val IN_TUNE_ZONE_CENTS = 5.0
