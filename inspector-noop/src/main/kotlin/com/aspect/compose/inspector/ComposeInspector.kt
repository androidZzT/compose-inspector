package com.aspect.compose.inspector

import androidx.compose.runtime.Composable

/**
 * No-op implementation of Compose Inspector for release builds.
 * Simply renders the content with zero overhead.
 */
@Composable
fun ComposeInspector(
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    content()
}
