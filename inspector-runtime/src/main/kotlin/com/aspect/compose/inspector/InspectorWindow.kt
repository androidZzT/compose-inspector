package com.aspect.compose.inspector

import android.content.Context
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aspect.compose.inspector.mvi.InspectorIntent
import com.aspect.compose.inspector.mvi.InspectorStore
import com.aspect.compose.inspector.ui.InspectorOverlay

private const val TAG = "InspectorWindow"

/**
 * Manages two separate windows for Inspector UI:
 *
 * 1. **FAB window** — small, positioned at bottom-end, always visible.
 *    Only covers the FAB area so touches pass through to the app elsewhere.
 * 2. **Overlay window** — full-width, 60% screen height, positioned at bottom.
 *    Shown when tree is parsed, dismissed via close button or FAB toggle.
 *
 * Using two windows instead of one full-screen window ensures the app beneath
 * remains fully interactive when the overlay is not showing.
 */
class InspectorWindow(
    private val context: Context,
    private val windowToken: IBinder,
    private val store: InspectorStore,
    private val onParseRequested: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var fabView: ComposeView? = null
    private var overlayView: ComposeView? = null
    private var isShowing = false

    fun show() {
        if (isShowing) return
        showFab()
        isShowing = true
    }

    private fun showFab() {
        val density = context.resources.displayMetrics.density
        val fabWindowSize = (64 * density).toInt()

        val view = createComposeView {
            MaterialTheme {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InspectorFloatingButton(
                        onClick = {
                            if (overlayView != null) {
                                dismissOverlay()
                            } else {
                                onParseRequested()
                                showOverlay()
                            }
                        }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            fabWindowSize,
            fabWindowSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = windowToken
            gravity = Gravity.BOTTOM or Gravity.END
            x = (16 * density).toInt()
            y = (16 * density).toInt()
        }

        try {
            windowManager.addView(view, params)
            fabView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add FAB window", e)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val view = createComposeView {
            MaterialTheme {
                val state by store.state.collectAsState()
                InspectorOverlay(
                    state = state,
                    onNodeClick = { nodeId ->
                        store.dispatch(InspectorIntent.SelectNode(nodeId))
                    },
                    onNodeExpand = { nodeId ->
                        store.dispatch(InspectorIntent.ToggleExpand(nodeId))
                    },
                    onRefresh = { onParseRequested() },
                    onDismiss = { dismissOverlay() }
                )
            }
        }

        val displayMetrics = context.resources.displayMetrics
        val overlayHeight = (displayMetrics.heightPixels * 0.6f).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = windowToken
            gravity = Gravity.BOTTOM
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window", e)
        }
    }

    private fun dismissOverlay() {
        val view = overlayView ?: return
        overlayView = null
        // 1. Dispose Compose content to cancel ripple animations and stop rendering
        view.disposeComposition()
        // 2. Remove from window on next frame after composition is fully torn down
        view.post {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay window", e)
            }
        }
    }

    private fun createComposeView(
        content: @androidx.compose.runtime.Composable () -> Unit
    ): ComposeView {
        return ComposeView(context).apply {
            val activity = context as? androidx.activity.ComponentActivity
            if (activity != null) {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
            }
            setContent { content() }
        }
    }

    fun dismiss() {
        if (!isShowing) return
        dismissOverlay()
        fabView?.let { view ->
            view.disposeComposition()
            view.post {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove FAB window", e)
                }
            }
        }
        fabView = null
        isShowing = false
    }
}

@androidx.compose.runtime.Composable
internal fun InspectorFloatingButton(
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
