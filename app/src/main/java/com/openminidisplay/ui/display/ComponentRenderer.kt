package com.openminidisplay.ui.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.RemoteDisplayService
import com.openminidisplay.display.DisplayStore
import com.openminidisplay.display.model.ComponentPresentation
import com.openminidisplay.display.model.ComponentSlot
import com.openminidisplay.display.model.ComponentType
import com.openminidisplay.display.model.DisplayKeys
import com.openminidisplay.display.model.TextStyleKind
import com.openminidisplay.display.model.WidgetValue
import com.openminidisplay.script.CardScriptManager
import com.openminidisplay.ui.widgets.BarChartWidget
import com.openminidisplay.ui.widgets.ButtonComponent
import com.openminidisplay.ui.widgets.LineChartWidget
import com.openminidisplay.ui.widgets.PieChartWidget
import com.openminidisplay.ui.widgets.ProgressBarWidget
import com.openminidisplay.ui.widgets.RingProgressWidget
import com.openminidisplay.ui.widgets.TextWidget
import com.openminidisplay.ui.widgets.ToggleComponent

@Composable
fun RenderComponent(
    cardId: String,
    slot: ComponentSlot,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dataMap by DisplayStore.data.collectAsStateWithLifecycle()
    val propsMap by DisplayStore.componentProps.collectAsStateWithLifecycle()
    val qualified = DisplayKeys.qualify(cardId, slot.id)
    val value = dataMap[qualified] ?: DisplayStore.valueFor(cardId, slot.id, slot.type)
    val props = propsMap[qualified] ?: DisplayStore.propsFor(cardId, slot.id, slot.label, slot.defaultChecked)
    val label = props.label ?: slot.label ?: slot.id
    val cardMultiComponent = showLabel
    val present = ComponentPresentation.resolve(slot, props, cardMultiComponent)
    val expanded = present.fill

    when (slot.type) {
        ComponentType.TEXT, ComponentType.METRIC -> {
            val text = (value as? WidgetValue.TextValue)?.text ?: "--"
            TextWidget(
                text = text,
                styleKind = if (slot.type == ComponentType.METRIC) TextStyleKind.METRIC else slot.style,
                expanded = expanded && !present.fit,
                align = present.align,
                fit = present.fit,
                modifier = modifier,
            )
        }
        ComponentType.PROGRESS -> {
            val percent = (value as? WidgetValue.Percent)?.value ?: 0f
            ProgressBarWidget(
                label = label,
                value = percent,
                showLabel = present.showLabel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        ComponentType.RING -> {
            val percent = (value as? WidgetValue.Percent)?.value ?: 0f
            RingProgressWidget(
                label = label,
                value = percent,
                showLabel = present.showLabel,
                expanded = expanded,
                scale = present.scale,
                modifier = modifier,
            )
        }
        ComponentType.LINE -> {
            val series = (value as? WidgetValue.Series)?.values ?: emptyList()
            LineChartWidget(
                label = label,
                series = series,
                showLabel = present.showLabel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        ComponentType.BAR -> {
            val series = (value as? WidgetValue.Series)?.values ?: emptyList()
            BarChartWidget(
                label = label,
                series = series,
                showLabel = present.showLabel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        ComponentType.PIE -> {
            val slices = (value as? WidgetValue.Pie)?.slices ?: emptyList()
            PieChartWidget(
                label = label,
                slices = slices,
                showLabel = present.showLabel,
                expanded = expanded,
                modifier = modifier,
            )
        }
        ComponentType.BUTTON -> {
            ButtonComponent(
                label = label,
                enabled = props.enabled,
                onClick = {
                    RemoteDisplayService.notifyUserActivity(context)
                    CardScriptManager.dispatchEvent(cardId, slot.id, "click")
                },
                modifier = modifier,
            )
        }
        ComponentType.TOGGLE -> {
            ToggleComponent(
                label = label,
                checked = props.checked,
                enabled = props.enabled,
                onCheckedChange = { checked ->
                    RemoteDisplayService.notifyUserActivity(context)
                    DisplayStore.setComponentProp(cardId, slot.id, "checked", checked.toString())
                    CardScriptManager.dispatchEvent(cardId, slot.id, "change", checked.toString())
                },
                modifier = modifier,
            )
        }
    }
}
