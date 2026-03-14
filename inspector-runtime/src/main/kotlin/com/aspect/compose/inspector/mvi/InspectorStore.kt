package com.aspect.compose.inspector.mvi

import com.aspect.compose.inspector.model.InspectorNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MVI Store that processes [InspectorIntent]s and produces [InspectorState].
 *
 * Tree data arrives pre-parsed via [InspectorIntent.AttachLayoutTree] from the
 * LayoutNode reflection parser; this store handles state management only.
 */
class InspectorStore {

    private val _state = MutableStateFlow(InspectorState())
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    fun dispatch(intent: InspectorIntent) {
        when (intent) {
            is InspectorIntent.AttachLayoutTree -> {
                // Auto-expand first 3 levels for immediate visibility
                val expandedIds = mutableSetOf<String>()
                fun collectIds(nodes: List<InspectorNode>, depth: Int) {
                    if (depth >= 3) return
                    for (node in nodes) {
                        if (node.children.isNotEmpty()) {
                            expandedIds.add(node.id)
                        }
                        collectIds(node.children, depth + 1)
                    }
                }
                collectIds(intent.tree.roots, 0)
                _state.value = _state.value.copy(
                    tree = intent.tree.roots,
                    expandedNodeIds = expandedIds,
                    isLoading = false,
                    isOverlayVisible = true,
                    error = null
                )
            }

            is InspectorIntent.SelectNode -> {
                val node = findNode(_state.value.tree, intent.nodeId)
                _state.value = _state.value.copy(selectedNode = node)
            }

            is InspectorIntent.ToggleExpand -> {
                val expanded = _state.value.expandedNodeIds.toMutableSet()
                if (intent.nodeId in expanded) {
                    expanded.remove(intent.nodeId)
                } else {
                    expanded.add(intent.nodeId)
                }
                _state.value = _state.value.copy(expandedNodeIds = expanded)
            }

            is InspectorIntent.ToggleOverlay -> {
                _state.value = _state.value.copy(isOverlayVisible = !_state.value.isOverlayVisible)
            }
        }
    }

    fun destroy() {
        // No-op for now; kept for API compatibility with ComposeInspector.onDispose
    }

    private fun findNode(
        nodes: List<InspectorNode>,
        nodeId: String
    ): InspectorNode? {
        for (node in nodes) {
            if (node.id == nodeId) return node
            findNode(node.children, nodeId)?.let { return it }
        }
        return null
    }
}
