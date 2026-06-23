package com.openminidisplay.ui.display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openminidisplay.ConnectionState
import com.openminidisplay.R
import com.openminidisplay.RuntimeState
import com.openminidisplay.display.DisplayStore
import com.openminidisplay.display.model.DisplayLayout
import com.openminidisplay.display.model.DisplayPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val INFINITE_PAGER_CENTER = Int.MAX_VALUE / 2
private val BottomBarHorizontalPadding = 16.dp
private val BottomBarContentOffset = 5.dp
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun tightBarTextStyle(base: TextStyle): TextStyle = base.copy(
    lineHeight = base.fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private fun mod(n: Int, divisor: Int): Int = ((n % divisor) + divisor) % divisor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisplayHost(
    onUserActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout by DisplayStore.layout.collectAsStateWithLifecycle()
    val connectionState by RuntimeState.connectionState.collectAsStateWithLifecycle()
    val targetPageIndex by DisplayStore.pageIndex.collectAsStateWithLifecycle()
    val pages = layout.pages
    var activePage by remember { mutableIntStateOf(0) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .displayGestures(
                connectionState = connectionState,
                onUserActivity = onUserActivity,
                onOpenSettings = onOpenSettings,
            )
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomBarHeight),
        ) {
            when {
                pages.isEmpty() -> Unit
                pages.size == 1 -> PageRenderer(page = pages.first(), modifier = Modifier.fillMaxSize())
                else -> MultiPageContent(
                    pages = pages,
                    targetPageIndex = targetPageIndex,
                    layout = layout,
                    onActivePageChange = { activePage = it },
                )
            }
        }

        DisplayBottomBar(
            showPageIndicator = pages.size > 1,
            pageCount = pages.size,
            activePage = activePage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    bottomBarHeight = with(density) { size.height.toDp() }
                },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiPageContent(
    pages: List<DisplayPage>,
    targetPageIndex: Int,
    layout: DisplayLayout,
    onActivePageChange: (Int) -> Unit,
) {
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
    LaunchedEffect(activeLogicalPage) {
        onActivePageChange(activeLogicalPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true,
    ) { virtualPage ->
        val page = pages[logicalPageForVirtual(virtualPage, pageCount)]
        PageRenderer(page = page, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun DisplayBottomBar(
    showPageIndicator: Boolean,
    pageCount: Int,
    activePage: Int,
    modifier: Modifier = Modifier,
) {
    val connectionState by RuntimeState.connectionState.collectAsStateWithLifecycle()
    val batteryLevel by RuntimeState.batteryLevel.collectAsStateWithLifecycle()
    val isPluggedIn by RuntimeState.isPluggedIn.collectAsStateWithLifecycle()
    var timeText by remember { mutableStateOf(LocalTime.now().format(TimeFormatter)) }

    LaunchedEffect(Unit) {
        while (true) {
            timeText = LocalTime.now().format(TimeFormatter)
            delay(1_000L)
        }
    }

    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val barTextStyle = tightBarTextStyle(MaterialTheme.typography.labelLarge)
    val connected = connectionState == ConnectionState.CONNECTED
    val connectionColor = if (connected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    }
    val connectionLabel = if (connected) {
        stringResource(R.string.status_connected)
    } else {
        stringResource(R.string.status_waiting_short)
    }
    val batteryLabel = when {
        batteryLevel < 0 -> null
        isPluggedIn -> stringResource(R.string.battery_charging, batteryLevel)
        else -> stringResource(R.string.battery_level, batteryLevel)
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = -BottomBarContentOffset)
                .padding(horizontal = BottomBarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeText,
                modifier = Modifier.weight(1f),
                style = barTextStyle,
                color = muted,
                textAlign = TextAlign.Start,
            )

            if (showPageIndicator) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pageCount) { index ->
                        val selected = activePage == index
                        Box(
                            modifier = Modifier
                                .size(if (selected) 8.dp else 6.dp)
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

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (batteryLabel != null) {
                    Text(
                        text = batteryLabel,
                        style = barTextStyle,
                        color = muted,
                    )
                }
                Row(
                    modifier = Modifier.padding(start = if (batteryLabel != null) 10.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(connectionColor),
                    )
                    Text(
                        text = connectionLabel,
                        style = barTextStyle,
                        color = muted,
                    )
                }
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
