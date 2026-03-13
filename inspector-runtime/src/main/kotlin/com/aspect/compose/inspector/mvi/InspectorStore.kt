package com.aspect.compose.inspector.mvi

import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import com.aspect.compose.inspector.core.CompositionTreeParser
import com.aspect.compose.inspector.core.RecompositionTracker
import com.aspect.compose.inspector.core.SubcompositionResolver
import com.aspect.compose.inspector.model.InspectorNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MVI Store that processes [InspectorIntent]s and produces [InspectorState].
 */
@OptIn(UiToolingDataApi::class)
class InspectorStore(
    private val treeParser: CompositionTreeParser = CompositionTreeParser(),
    private val subcompositionResolver: SubcompositionResolver = SubcompositionResolver(),
    private val recompositionTracker: RecompositionTracker = RecompositionTracker()
) {

    private val _state = MutableStateFlow(InspectorState())
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    private var lastRootGroup: Group? = null

    fun dispatch(intent: InspectorIntent) {
        val currentState = _state.value
        _state.value = reduce(currentState, intent)
    }

    private fun reduce(state: InspectorState, intent: InspectorIntent): InspectorState {
        return when (intent) {
            is InspectorIntent.AttachTo -> {
                lastRootGroup = intent.rootGroup
                parseTree(state, intent.rootGroup)
            }

            is InspectorIntent.RefreshTree -> {
                val group = lastRootGroup ?: return state.copy(
                    error = "No composition attached"
                )
                parseTree(state, group)
            }

            is InspectorIntent.SelectNode -> {
                val node = findNode(state.tree, intent.nodeId)
                state.copy(selectedNode = node)
            }

            is InspectorIntent.ToggleExpand -> {
                val expanded = state.expandedNodeIds.toMutableSet()
                if (intent.nodeId in expanded) {
                    expanded.remove(intent.nodeId)
                } else {
                    expanded.add(intent.nodeId)
                }
                state.copy(expandedNodeIds = expanded)
            }

            is InspectorIntent.ToggleOverlay -> {
                state.copy(isOverlayVisible = !state.isOverlayVisible)
            }
        }
    }

    private fun parseTree(state: InspectorState, rootGroup: Group): InspectorState {
        return try {
            val tree = treeParser.parse(rootGroup)
            state.copy(
                tree = tree.roots,
                isLoading = false,
                error = null
            )
        } catch (e: Exception) {
            state.copy(
                isLoading = false,
                error = "Failed to parse tree: ${e.message}"
            )
        }
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
