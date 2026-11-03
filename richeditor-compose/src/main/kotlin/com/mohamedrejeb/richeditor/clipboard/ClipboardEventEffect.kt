package com.mohamedrejeb.richeditor.clipboard

import androidx.compose.runtime.Composable
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Registers platform-specific clipboard event handlers for keyboard shortcuts (Ctrl+C/V/X).
 *
 * On Android this is a no-op: the OS routes clipboard operations through the
 * Compose [androidx.compose.ui.platform.Clipboard] framework.
 */
@Composable
internal fun ClipboardEventEffect(richTextState: RichTextState) {
    // No-op: Android routes clipboard operations through the Compose framework
}
