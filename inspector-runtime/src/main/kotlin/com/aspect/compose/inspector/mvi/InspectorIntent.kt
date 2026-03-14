package com.aspect.compose.inspector.mvi

import com.aspect.compose.inspector.model.InspectorTree

/**
 * User intents for the Inspector.
 */
sealed interface InspectorIntent {
    /** Attach a pre-parsed LayoutNode tree directly. */
    data class AttachLayoutTree(val tree: InspectorTree) : InspectorIntent

    /** Select a node by its ID. */
    data class SelectNode(val nodeId: String) : InspectorIntent

    /** Toggle expand/collapse of a node. */
    data class ToggleExpand(val nodeId: String) : InspectorIntent

    /** Toggle the overlay visibility. */
    data object ToggleOverlay : InspectorIntent
}
