package com.aspect.compose.inspector.mvi

import androidx.compose.runtime.Immutable
import com.aspect.compose.inspector.model.InspectorNode

/**
 * UI state for the Inspector.
 */
@Immutable
data class InspectorState(
    val tree: List<InspectorNode> = emptyList(),
    val selectedNode: InspectorNode? = null,
    val expandedNodeIds: Set<String> = emptySet(),
    val isOverlayVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
