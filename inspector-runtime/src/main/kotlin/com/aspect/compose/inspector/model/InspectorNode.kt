package com.aspect.compose.inspector.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntRect

/**
 * Represents a single node in the Compose view tree.
 */
@Immutable
data class InspectorNode(
    val id: String,
    val name: String,
    val sourceLocation: String? = null,
    val parameters: List<NodeParameter> = emptyList(),
    val bounds: IntRect? = null,
    val recompositionCount: Int = 0,
    val children: List<InspectorNode> = emptyList(),
    val depth: Int = 0
)
