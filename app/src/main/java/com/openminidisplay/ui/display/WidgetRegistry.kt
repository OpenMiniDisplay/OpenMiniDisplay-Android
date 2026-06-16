package com.openminidisplay.ui.display

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.openminidisplay.display.model.TextStyleKind
import com.openminidisplay.display.model.WidgetSlot
import com.openminidisplay.display.model.WidgetType
import com.openminidisplay.display.model.WidgetValue
import com.openminidisplay.display.repo.DisplayDataRepository
import com.openminidisplay.ui.widgets.BarChartWidget
import com.openminidisplay.ui.widgets.LineChartWidget
import com.openminidisplay.ui.widgets.PieChartWidget
import com.openminidisplay.ui.widgets.ProgressBarWidget
import com.openminidisplay.ui.widgets.RingProgressWidget
import com.openminidisplay.ui.widgets.TextWidget

@Composable
fun RenderWidget(
    slot: WidgetSlot,
    showChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    val value = DisplayDataRepository.valueFor(slot.id, slot.type)
    val label = slot.label ?: slot.id
    val expanded = !showChrome

    when (slot.type) {
        WidgetType.TEXT, WidgetType.METRIC -> {
            val text = (value as? WidgetValue.TextValue)?.text ?: "--"
            TextWidget(
                text = text,
                styleKind = if (slot.type == WidgetType.METRIC) TextStyleKind.METRIC else slot.style,
                expanded = expanded,
                modifier = modifier,
            )
        }
        WidgetType.PROGRESS -> {
            val percent = (value as? WidgetValue.Percent)?.value ?: 0f
            ProgressBarWidget(
                label = label,
                value = percent,
                showLabel = showChrome,
                expanded = expanded,
                modifier = modifier,
            )
        }
        WidgetType.RING -> {
            val percent = (value as? WidgetValue.Percent)?.value ?: 0f
            RingProgressWidget(
                label = label,
                value = percent,
                showLabel = showChrome,
                expanded = expanded,
                modifier = modifier,
            )
        }
        WidgetType.LINE -> {
            val series = (value as? WidgetValue.Series)?.values ?: emptyList()
            LineChartWidget(
                label = label,
                series = series,
                showLabel = showChrome,
                expanded = expanded,
                modifier = modifier,
            )
        }
        WidgetType.BAR -> {
            val series = (value as? WidgetValue.Series)?.values ?: emptyList()
            BarChartWidget(
                label = label,
                series = series,
                showLabel = showChrome,
                expanded = expanded,
                modifier = modifier,
            )
        }
        WidgetType.PIE -> {
            val slices = (value as? WidgetValue.Pie)?.slices ?: emptyList()
            PieChartWidget(
                label = label,
                slices = slices,
                showLabel = showChrome,
                expanded = expanded,
                modifier = modifier,
            )
        }
    }
}
