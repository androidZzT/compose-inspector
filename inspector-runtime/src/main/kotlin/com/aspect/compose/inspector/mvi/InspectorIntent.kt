package com.aspect.compose.inspector.mvi

import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import com.aspect.compose.inspector.model.InspectorTree

/**
 * User intents for the Inspector.
 */
@OptIn(UiToolingDataApi::class)
sealed interface InspectorIntent {
    /** Attach inspector to a composition's parsed group tree. */
    data class AttachTo(val rootGroup: Group) : InspectorIntent

    /** Attach a pre-parsed LayoutNode tree directly. */
    data class AttachLayoutTree(val tree: InspectorTree) : InspectorIntent

    /** Refresh the current tree using the last attached group. */
    data object RefreshTree : InspectorIntent

    /** Select a node by its ID. */
    data class SelectNode(val nodeId: String) : InspectorIntent

    /** Toggle expand/collapse of a node. */
    data class ToggleExpand(val nodeId: String) : InspectorIntent

    /** Toggle the overlay visibility. */
    data object ToggleOverlay : InspectorIntent
}
