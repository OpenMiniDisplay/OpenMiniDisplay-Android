package com.openminidisplay.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.settings.AppColorScheme
import com.openminidisplay.settings.AppPreferences
import com.openminidisplay.ui.theme.XianiiColors

private val CardShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(8.dp)

@Composable
private fun oledBorderColor() = XianiiColors.OledBorder

@Composable
private fun Modifier.settingsCardSurface(shape: Shape = CardShape): Modifier {
    val colorScheme by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    return if (colorScheme == AppColorScheme.OLED) {
        clip(shape)
            .border(1.dp, oledBorderColor(), shape)
            .background(Color.Black)
    } else {
        clip(shape).background(MaterialTheme.colorScheme.surface)
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        SettingsCard(content = content)
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .settingsCardSurface(CardShape),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickable = onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ChipShape)
            .then(
                if (clickable) {
                    Modifier.clickable(onClick = onClick!!)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        colorScheme == AppColorScheme.OLED -> oledBorderColor()
        else -> MaterialTheme.colorScheme.outline
    }
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (colorScheme == AppColorScheme.OLED) 0.22f else 0.18f)
        colorScheme == AppColorScheme.OLED -> Color.Black
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = modifier
            .clip(ChipShape)
            .border(1.dp, borderColor, ChipShape)
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
fun SettingsDivider() {
    val colorScheme by AppPreferences.colorScheme.collectAsStateWithLifecycle()
    val color = if (colorScheme == AppColorScheme.OLED) {
        oledBorderColor()
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(color),
    )
}
