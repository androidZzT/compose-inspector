package com.aspect.compose.inspector

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspect.compose.inspector.core.CompositionTreeParser
import com.aspect.compose.inspector.core.RecompositionTracker
import com.aspect.compose.inspector.core.SubcompositionResolver
import com.aspect.compose.inspector.mvi.InspectorIntent
import com.aspect.compose.inspector.mvi.InspectorStore
import com.aspect.compose.inspector.ui.InspectorOverlay

private const val TAG = "ComposeInspector"

/**
 * Entry point for Compose Inspector.
 *
 * Wrap your root composable with this to enable on-device inspection:
 * ```
 * setContent {
 *     ComposeInspector(enabled = BuildConfig.DEBUG) {
 *         MyApp()
 *     }
 * }
 * ```
 *
 * When enabled, a floating button appears that opens the inspector panel.
 * The inspector parses the current Compose tree and displays it in
 * an expandable tree view with node details and recomposition counts.
 */
@OptIn(UiToolingDataApi::class)
@Composable
fun ComposeInspector(
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val recompositionTracker = remember { RecompositionTracker() }
    val subcompositionResolver = remember { SubcompositionResolver() }
    val treeParser = remember {
        CompositionTreeParser(recompositionTracker, subcompositionResolver)
    }
    val store = remember {
        InspectorStore(
            treeParser = treeParser,
            subcompositionResolver = subcompositionResolver,
            recompositionTracker = recompositionTracker
        )
    }
    val state by store.state.collectAsState()

    val composer = currentComposer

    Box(modifier = Modifier.fillMaxSize()) {
        // Render the app content
        content()

        // Floating inspector trigger button
        InspectorFloatingButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = {
                try {
                    val rootGroup = composer.compositionData.asTree()
                    store.dispatch(InspectorIntent.AttachTo(rootGroup))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read composition data", e)
                }
                store.dispatch(InspectorIntent.ToggleOverlay)
            }
        )

        // Inspector overlay panel
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            InspectorOverlay(
                state = state,
                onNodeClick = { nodeId ->
                    store.dispatch(InspectorIntent.SelectNode(nodeId))
                },
                onNodeExpand = { nodeId ->
                    store.dispatch(InspectorIntent.ToggleExpand(nodeId))
                },
                onRefresh = {
                    try {
                        val rootGroup = composer.compositionData.asTree()
                        store.dispatch(InspectorIntent.AttachTo(rootGroup))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to refresh composition data", e)
                    }
                },
                onDismiss = {
                    store.dispatch(InspectorIntent.ToggleOverlay)
                }
            )
        }
    }
}

@Composable
private fun InspectorFloatingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF6750A4))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83D\uDD0D",
            fontSize = 20.sp
        )
    }
}
