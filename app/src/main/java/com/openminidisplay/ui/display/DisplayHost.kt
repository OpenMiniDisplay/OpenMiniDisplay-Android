package com.openminidisplay.ui.display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.ConnectionState
import com.openminidisplay.RuntimeState
import com.openminidisplay.display.DisplayStore
import kotlinx.coroutines.flow.distinctUntilChanged

private const val INFINITE_PAGER_CENTER = Int.MAX_VALUE / 2

private fun mod(n: Int, divisor: Int): Int = ((n % divisor) + divisor) % divisor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisplayHost(
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout by DisplayStore.layout.collectAsStateWithLifecycle()
    val connectionState by RuntimeState.connectionState.collectAsStateWithLifecycle()
    val targetPageIndex by DisplayStore.pageIndex.collectAsStateWithLifecycle()
    val pages = layout.pages

    val userActivityModifier = if (connectionState == ConnectionState.DISCONNECTED) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onUserActivity()
            }
        }
    } else {
        Modifier
    }

    if (pages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(userActivityModifier)
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    if (pages.size == 1) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(userActivityModifier)
                .background(MaterialTheme.colorScheme.background),
        ) {
            PageRenderer(page = pages.first(), modifier = Modifier.fillMaxSize())
        }
        return
    }

    val pageCount = pages.size
    val initialLogicalPage = targetPageIndex.coerceIn(0, pages.lastIndex)
    val initialVirtualPage = remember(pageCount, initialLogicalPage) {
        virtualPageForLogical(initialLogicalPage, pageCount, INFINITE_PAGER_CENTER)
    }

    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage,
        pageCount = { Int.MAX_VALUE },
    )

    LaunchedEffect(targetPageIndex, pageCount) {
        val target = targetPageIndex.coerceIn(0, pages.lastIndex)
        val currentLogical = mod(pagerState.currentPage, pageCount)
        if (currentLogical == target) return@LaunchedEffect

        val delta = shortestPageDelta(currentLogical, target, pageCount)
        pagerState.animateScrollToPage(pagerState.currentPage + delta)
    }

    LaunchedEffect(pagerState, pageCount, layout) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (_, inProgress) ->
                if (!inProgress) {
                    DisplayStore.goTo(
                        mod(pagerState.currentPage, pageCount),
                        layout,
                    )
                }
            }
    }

    val activeLogicalPage = mod(pagerState.currentPage, pageCount)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(userActivityModifier)
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
        ) { virtualPage ->
            val page = pages[logicalPageForVirtual(virtualPage, pageCount)]
            PageRenderer(page = page, modifier = Modifier.fillMaxSize())
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pages.forEachIndexed { index, _ ->
                val selected = activeLogicalPage == index
                Box(
                    modifier = Modifier
                        .size(if (selected) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            },
                        ),
                )
            }
        }
    }
}

private fun logicalPageForVirtual(virtualPage: Int, pageCount: Int): Int {
    return mod(virtualPage, pageCount)
}

private fun virtualPageForLogical(logicalPage: Int, pageCount: Int, anchorVirtualPage: Int): Int {
    val anchorLogical = mod(anchorVirtualPage, pageCount)
    return anchorVirtualPage - anchorLogical + logicalPage
}

private fun shortestPageDelta(current: Int, target: Int, pageCount: Int): Int {
    val forward = mod(target - current, pageCount)
    val backward = forward - pageCount
    return if (forward <= -backward) forward else backward
}
