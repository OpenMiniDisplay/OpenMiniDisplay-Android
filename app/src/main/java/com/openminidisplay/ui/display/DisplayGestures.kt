package com.openminidisplay.ui.display

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.openminidisplay.ConnectionState
import kotlin.math.sqrt

@Composable
fun Modifier.displayGestures(
    connectionState: ConnectionState,
    onUserActivity: () -> Unit,
    onOpenSettings: () -> Unit,
): Modifier {
    val view = LocalView.current
    return pointerInput(connectionState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val start = down.position
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onOpenSettings()
            } else if (connectionState == ConnectionState.DISCONNECTED) {
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && start.distanceTo(up.position) <= viewConfiguration.touchSlop) {
                    onUserActivity()
                }
            }
        }
    }
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}
