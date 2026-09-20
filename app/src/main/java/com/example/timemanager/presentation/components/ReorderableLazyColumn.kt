@file:OptIn(ExperimentalFoundationApi::class)

package com.example.timemanager.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun <T> ReorderableLazyColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemContent: @Composable LazyItemScope.(T, Boolean) -> Unit
) {
    val lazyListState = rememberLazyListState()
    var currentItems by remember { mutableStateOf(items) }
    val latestItems by rememberUpdatedState(items)

    var draggingItemIndex by remember { mutableIntStateOf(-1) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    // Синхронизацию с источником данных вынесли из фазы композиции: запись в
    // state прямо во время композиции запускала немедленный повторный проход.
    LaunchedEffect(items, draggingItemIndex) {
        if (draggingItemIndex == -1 && currentItems != items) {
            currentItems = items
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(
            items = currentItems,
            key = { _, item -> key(item) }
        ) { index, item ->
            val currentIndex by rememberUpdatedState(index)
            val currentItem by rememberUpdatedState(item)
            val isDragging = currentIndex == draggingItemIndex

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItemIndex = currentIndex
                                draggingOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggingOffset += dragAmount.y

                                val activeIndex = draggingItemIndex
                                if (activeIndex == -1) return@detectDragGesturesAfterLongPress

                                val layoutInfo = lazyListState.layoutInfo
                                val startOffset = layoutInfo.visibleItemsInfo
                                    .find { it.index == activeIndex }
                                    ?.offset ?: return@detectDragGesturesAfterLongPress
                                val currentOffset = startOffset + draggingOffset

                                val targetItem = layoutInfo.visibleItemsInfo
                                    .firstOrNull { target ->
                                        target.index != activeIndex &&
                                            currentOffset.toInt() in target.offset until target.offset + target.size
                                    }

                                if (targetItem != null && targetItem.index < currentItems.size) {
                                    currentItems = currentItems.toMutableList().apply {
                                        move(activeIndex, targetItem.index)
                                    }
                                    draggingItemIndex = targetItem.index
                                    val newStartOffset = layoutInfo.visibleItemsInfo
                                        .find { it.index == targetItem.index }
                                        ?.offset ?: startOffset
                                    draggingOffset = currentOffset - newStartOffset
                                }
                            },
                            onDragEnd = {
                                onReorder(currentItems)
                                draggingItemIndex = -1
                                draggingOffset = 0f
                            },
                            onDragCancel = {
                                currentItems = latestItems
                                draggingItemIndex = -1
                                draggingOffset = 0f
                            }
                        )
                    }
                    .then(
                        if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .offset { IntOffset(0, draggingOffset.roundToInt()) }
                                .shadow(5.dp, RoundedCornerShape(3.dp))
                        } else {
                            Modifier.animateItem()
                        }
                    )
            ) {
                itemContent(currentItem, isDragging)
            }
        }
    }
}

private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}
