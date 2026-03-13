package com.aspect.compose.inspector.model

import androidx.compose.runtime.Immutable

/**
 * Represents the full Compose view tree.
 */
@Immutable
data class InspectorTree(
    val roots: List<InspectorNode> = emptyList()
) {
    val nodeCount: Int
        get() = countNodes(roots)

    private fun countNodes(nodes: List<InspectorNode>): Int {
        return nodes.size + nodes.sumOf { countNodes(it.children) }
    }
}
