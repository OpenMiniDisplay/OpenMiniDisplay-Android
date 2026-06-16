package com.openminidisplay.display.model

sealed class WidgetValue {
    data class TextValue(val text: String) : WidgetValue()
    data class Percent(val value: Float) : WidgetValue()
    data class Series(val values: List<Float>) : WidgetValue()
    data class Pie(val slices: List<PieSlice>) : WidgetValue()
}
