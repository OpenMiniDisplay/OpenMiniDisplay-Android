package com.openminidisplay.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.openminidisplay.display.model.PieSlice
import kotlin.math.min as mathMin

private val chartColors = listOf(
    androidx.compose.ui.graphics.Color(0xFF64B5F6),
    androidx.compose.ui.graphics.Color(0xFF81C784),
    androidx.compose.ui.graphics.Color(0xFFFFB74D),
    androidx.compose.ui.graphics.Color(0xFFE57373),
    androidx.compose.ui.graphics.Color(0xFFBA68C8),
    androidx.compose.ui.graphics.Color(0xFF4DB6AC),
)

@Composable
fun ProgressBarWidget(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    expanded: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, if (expanded) Alignment.CenterVertically else Alignment.Top),
        horizontalAlignment = if (expanded) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth(if (expanded) 0.6f else 1f)
                .height(if (expanded) 16.dp else 10.dp),
            strokeCap = StrokeCap.Round,
        )
        Text(
            "${value.toInt()}%",
            style = if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun RingProgressWidget(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    expanded: Boolean = false,
) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val ringSize = if (expanded) {
            min(maxWidth, maxHeight) * 0.55f
        } else {
            96.dp
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = if (expanded) 14.dp.toPx() else 10.dp.toPx()
                    val arcSize = mathMin(size.width, size.height) - stroke
                    val topLeft = Offset((size.width - arcSize) / 2f, (size.height - arcSize) / 2f)
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * (value / 100f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    "${value.toInt()}%",
                    style = if (expanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun LineChartWidget(
    label: String,
    series: List<Float>,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    expanded: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (series.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Need 2+ points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxValue = series.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val stepX = size.width / (series.size - 1).coerceAtLeast(1)
            val points = series.mapIndexed { index, point ->
                val x = stepX * index
                val y = size.height - (point / maxValue) * size.height
                Offset(x, y)
            }

            for (index in 0 until points.lastIndex) {
                drawLine(
                    color = chartColors[0],
                    start = points[index],
                    end = points[index + 1],
                    strokeWidth = if (expanded) 6f else 4f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { point ->
                drawCircle(color = chartColors[0], radius = if (expanded) 7f else 5f, center = point)
            }
        }
    }
}

@Composable
fun BarChartWidget(
    label: String,
    series: List<Float>,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    expanded: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxValue = series.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val gap = if (expanded) 12.dp.toPx() else 8.dp.toPx()
            val barWidth = (size.width - gap * (series.size + 1)) / series.size.coerceAtLeast(1)
            series.forEachIndexed { index, value ->
                val barHeight = (value / maxValue) * size.height
                val left = gap + index * (barWidth + gap)
                drawRect(
                    color = chartColors[index % chartColors.size],
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
    }
}

@Composable
fun PieChartWidget(
    label: String,
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    expanded: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (slices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
        RowWithPie(slices = slices, total = total, expanded = expanded)
    }
}

@Composable
private fun RowWithPie(
    slices: List<PieSlice>,
    total: Float,
    expanded: Boolean,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val pieSize = if (expanded) min(maxWidth, maxHeight) * 0.8f else 96.dp
            Canvas(modifier = Modifier.size(pieSize)) {
                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = 360f * (slice.value / total)
                    drawArc(
                        color = chartColors[index % chartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                    )
                    startAngle += sweep
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            slices.forEachIndexed { index, slice ->
                val percent = (slice.value / total * 100f).toInt()
                Text(
                    text = "${slice.label}: ${slice.value.toInt()} ($percent%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = chartColors[index % chartColors.size],
                )
            }
        }
    }
}
