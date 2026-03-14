package com.aspect.compose.inspector

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.aspect.compose.inspector.core.LayoutNodeTreeParser
import com.aspect.compose.inspector.core.RecompositionTracker
import com.aspect.compose.inspector.mvi.InspectorIntent
import com.aspect.compose.inspector.mvi.InspectorStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
 * The inspector UI (FAB + overlay) is rendered in a separate window via
 * [WindowManager], so it does NOT appear in the inspected LayoutNode tree.
 */
@Composable
fun ComposeInspector(
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val store = remember { InspectorStore() }
    val tracker = remember { RecompositionTracker() }
    val layoutNodeParser = remember { LayoutNodeTreeParser(tracker) }
    val rootView = LocalView.current
    val context = LocalContext.current

    // Render user content directly — no wrapper Box, no extra UI nodes
    content()

    // Auto-refresh: re-parse every 2 seconds while overlay is visible
    LaunchedEffect(rootView) {
        while (isActive) {
            delay(2000)
            if (store.state.value.isOverlayVisible) {
                parseFromLayoutNodes(rootView, layoutNodeParser, store)
            }
        }
    }

    // Manage the inspector window lifecycle in a separate window
    DisposableEffect(rootView) {
        var window: InspectorWindow? = null

        // Defer window creation until the view is attached and has a window token
        rootView.post {
            val token = rootView.rootView.windowToken
            if (token != null) {
                window = InspectorWindow(
                    context = context,
                    windowToken = token,
                    store = store,
                    onParseRequested = {
                        parseFromLayoutNodes(rootView, layoutNodeParser, store)
                    }
                )
                window?.show()
            } else {
                Log.w(TAG, "Window token not available, cannot show inspector")
            }
        }

        onDispose {
            window?.dismiss()
            store.destroy()
        }
    }
}

/**
 * Finds the AndroidComposeView in the view hierarchy and parses its LayoutNode tree.
 */
private fun parseFromLayoutNodes(
    rootView: View,
    parser: LayoutNodeTreeParser,
    store: InspectorStore
) {
    try {
        val composeView = findAndroidComposeView(rootView)
        if (composeView != null) {
            val tree = parser.parse(composeView)
            Log.d(TAG, "Parsed tree: ${tree.roots.size} roots, ${tree.nodeCount} total nodes")
            store.dispatch(InspectorIntent.AttachLayoutTree(tree))
        } else {
            Log.w(TAG, "AndroidComposeView not found in view hierarchy")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse LayoutNode tree", e)
    }
}

private fun findAndroidComposeView(view: View): View? {
    val className = view.javaClass.canonicalName
    if (className != null && className.contains("AndroidComposeView")) {
        return view
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val result = findAndroidComposeView(view.getChildAt(i))
            if (result != null) return result
        }
    }
    return null
}
