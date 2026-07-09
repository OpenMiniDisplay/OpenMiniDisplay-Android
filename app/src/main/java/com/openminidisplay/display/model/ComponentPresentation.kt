package com.openminidisplay.display.model

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

enum class ComponentAlign {
    START,
    CENTER,
    END,
    ;

    companion object {
        fun fromRaw(raw: String?): ComponentAlign? {
            return when (raw?.lowercase()) {
                "start", "top", "left" -> START
                "center", "middle" -> CENTER
                "end", "bottom", "right" -> END
                else -> null
            }
        }
    }

    fun toAlignment(): Alignment = when (this) {
        START -> Alignment.TopStart
        CENTER -> Alignment.Center
        END -> Alignment.BottomEnd
    }

    fun toTextAlign(): TextAlign = when (this) {
        START -> TextAlign.Start
        CENTER -> TextAlign.Center
        END -> TextAlign.End
    }
}

data class ComponentPresentation(
    val align: ComponentAlign,
    val fill: Boolean,
    val fit: Boolean,
    val scale: Float,
    val showLabel: Boolean,
) {
    companion object {
        fun resolve(
            slot: ComponentSlot,
            props: ComponentProps,
            cardMultiComponent: Boolean,
        ): ComponentPresentation {
            val borderless = !cardMultiComponent
            val fill = props.fill ?: slot.fill ?: borderless
            val fit = props.fit ?: slot.fit
            val align = props.align ?: slot.align ?: if (fill || fit) ComponentAlign.CENTER else ComponentAlign.START
            val showLabel = props.showLabel ?: slot.showLabel ?: cardMultiComponent
            val scale = (props.scale ?: slot.scale ?: if (fill) 0.85f else 0.55f).coerceIn(0.2f, 1f)
            return ComponentPresentation(
                align = align,
                fill = fill,
                fit = fit,
                scale = scale,
                showLabel = showLabel,
            )
        }
    }
}
