package com.openminidisplay.ui.widgets

import android.graphics.drawable.Animatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageWidget(
    src: String,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    var drawable by remember(src) { mutableStateOf<Drawable?>(null) }
    var error by remember(src) { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(src) {
        drawable = null
        error = null
        if (src.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                drawable = decodeImage(src)
            } catch (e: Exception) {
                error = e.message ?: "load failed"
            }
        }
    }

    DisposableEffect(drawable) {
        startAnimation(drawable)
        onDispose { stopAnimation(drawable) }
    }

    if (src.isBlank()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("--", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (error != null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = error ?: "ERR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = if (fill) {
                    ImageView.ScaleType.FIT_CENTER
                } else {
                    ImageView.ScaleType.CENTER_INSIDE
                }
                adjustViewBounds = true
            }
        },
        update = { view ->
            view.setImageDrawable(drawable)
            startAnimation(drawable)
        },
    )
}

private fun decodeImage(src: String): Drawable {
    val file = when {
        src.startsWith("file://", ignoreCase = true) -> File(src.removePrefix("file://"))
        else -> File(src)
    }
    return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
}

private fun startAnimation(drawable: Drawable?) {
    when (drawable) {
        is AnimatedImageDrawable -> drawable.start()
        is Animatable -> drawable.start()
        is Animatable2 -> drawable.start()
    }
}

private fun stopAnimation(drawable: Drawable?) {
    when (drawable) {
        is AnimatedImageDrawable -> drawable.stop()
        is Animatable -> drawable.stop()
        is Animatable2 -> drawable.stop()
    }
}
