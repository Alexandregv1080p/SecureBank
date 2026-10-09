package com.securebank.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securebank.mobile.core.util.Dashboard
import com.securebank.mobile.core.util.Dashboard.Pt

data class ChartSeries(val name: String, val color: Color, val values: List<Double>)

/** Monta o caminho da curva suave por [points] (em pixels). */
private fun curve(points: List<Pt>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) return@apply
    Dashboard.smooth(points).forEach { cubicTo(it.c1.x, it.c1.y, it.c2.x, it.c2.y, it.end.x, it.end.y) }
}

/** Mesmo caminho só até [fraction] do comprimento: é assim que a linha "se desenha" na entrada. */
private fun partial(path: Path, fraction: Float): Path {
    if (fraction >= 1f) return path
    val measure = PathMeasure()
    measure.setPath(path, false)
    return Path().also { measure.getSegment(0f, measure.length * fraction, it, true) }
}

/**
 * Gráfico de área com duas ou mais séries (entradas e saídas por mês). Tocar ou arrastar escolhe o mês: os valores dele
 * aparecem na linha de cima (por padrão, o último mês). A descrição de acessibilidade traz a mesma informação.
 */
@Composable
fun AreaChart(labels: List<String>, series: List<ChartSeries>, format: (Double) -> String, title: String, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val shown = selected ?: (labels.size - 1)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(labels, series) { progress.snapTo(0f); progress.animateTo(1f, tween(900)) }
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline
    val panel = com.securebank.mobile.ui.theme.Sb.colors.panel
    val max = Dashboard.niceMax(series.flatMap { it.values }.maxOrNull() ?: 0.0)
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(labels.getOrElse(shown) { "" }, style = MaterialTheme.typography.labelLarge)
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(s.color, CircleShape))
                    MoneyText(format(s.values.getOrElse(shown) { 0.0 }), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .semantics {
                    contentDescription = title + ": " + labels.indices.joinToString("; ") { i ->
                        labels[i] + " " + series.joinToString(", ") { s -> s.name + " " + format(s.values.getOrElse(i) { 0.0 }) }
                    }
                }
                .pointerInput(labels.size) {
                    fun pick(x: Float) {
                        val left = 40.dp.toPx()
                        val inner = size.width - left - 8.dp.toPx()
                        selected = if (labels.size <= 1) 0 else (((x - left) / inner) * (labels.size - 1)).let { Math.round(it) }.coerceIn(0, labels.size - 1)
                    }
                    detectTapGestures(onPress = { pick(it.x) }, onTap = { })
                }
                .pointerInput(labels.size) {
                    detectDragGestures(onDragEnd = { }) { change, _ ->
                        val left = 40.dp.toPx()
                        val inner = size.width - left - 8.dp.toPx()
                        selected = if (labels.size <= 1) 0 else (((change.position.x - left) / inner) * (labels.size - 1)).let { Math.round(it) }.coerceIn(0, labels.size - 1)
                    }
                },
        ) {
            val left = 40.dp.toPx()
            val right = 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = 22.dp.toPx()
            val innerW = size.width - left - right
            val innerH = size.height - top - bottom
            fun x(i: Int) = if (labels.size <= 1) left + innerW / 2 else left + i * innerW / (labels.size - 1)
            fun y(v: Double) = top + innerH * (1f - (v / max).toFloat())

            for (t in 0..4) {
                val value = max * t / 4
                drawLine(grid, Offset(left, y(value)), Offset(size.width - right, y(value)), 1.dp.toPx())
                val text = measurer.measure(Dashboard.compact(value), labelStyle)
                drawText(text, topLeft = Offset(left - 6.dp.toPx() - text.size.width, y(value) - text.size.height / 2f))
            }
            labels.forEachIndexed { i, label ->
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, size.height - text.size.height - 2.dp.toPx()))
            }
            series.forEach { s ->
                val pts = s.values.mapIndexed { i, v -> Pt(x(i), y(v)) }
                val line = curve(pts)
                if (pts.size > 1) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(x(labels.size - 1), y(0.0))
                        lineTo(x(0), y(0.0))
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.30f * progress.value), Color.Transparent), startY = top, endY = y(0.0)))
                }
                drawPath(partial(line, progress.value), s.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            selected?.let { i ->
                drawLine(muted.copy(alpha = 0.5f), Offset(x(i), top), Offset(x(i), y(0.0)), 1.dp.toPx())
                series.forEach { s ->
                    val c = Offset(x(i), y(s.values.getOrElse(i) { 0.0 }))
                    drawCircle(panel, 5.dp.toPx(), c)
                    drawCircle(s.color, 5.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
                }
            }
        }
    }
}

/** Rosca de participação: as fatias têm um respiro entre si para ficarem legíveis; o miolo leva o total. */
@Composable
fun Donut(slices: List<Pair<Float, Color>>, centerLabel: String, centerValue: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val track = MaterialTheme.colorScheme.outline
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(800)) }
    Box(modifier.size(size).semantics { contentDescription = "$centerLabel: $centerValue" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track.copy(alpha = 0.6f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            val gap = if (slices.size > 1) 5f else 0f
            var start = -90f
            slices.forEach { (percent, color) ->
                val sweep = (percent * 3.6f - gap).coerceAtLeast(0f) * progress.value
                drawArc(color, start + gap / 2, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                start += percent * 3.6f
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(centerValue, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Mini gráfico de tendência para os cartões de métrica (sem eixos: só o formato da série). */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) { progress.snapTo(0f); progress.animateTo(1f, tween(900)) }
    Canvas(modifier.width(88.dp).height(34.dp)) {
        if (values.isEmpty()) return@Canvas
        val pad = 4.dp.toPx()
        val max = values.max()
        val min = minOf(values.min(), 0.0)
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pts = values.mapIndexed { i, v ->
            Pt(
                if (values.size == 1) size.width / 2 else pad + i * (size.width - 2 * pad) / (values.size - 1),
                size.height - pad - ((v - min) / span).toFloat() * (size.height - 2 * pad),
            )
        }
        val line = curve(pts)
        if (pts.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f * progress.value), Color.Transparent)))
        }
        drawPath(partial(line, progress.value), color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawDot(pts.last(), color)
    }
}

private fun DrawScope.drawDot(p: Pt, color: Color) = drawCircle(color, 3.dp.toPx(), Offset(p.x, p.y))

/** Anel de progresso (0–100) com ícone ou texto no miolo: usado nas listas de patrimônio. */
@Composable
fun ProgressRing(percent: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 48.dp, content: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.outline
    val p = percent.coerceIn(0f, 100f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (p > 0f) drawArc(color, -90f, p * 3.6f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}
