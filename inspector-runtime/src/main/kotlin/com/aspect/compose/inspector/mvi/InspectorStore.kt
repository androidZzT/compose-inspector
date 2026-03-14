package com.aspect.compose.inspector.mvi

import android.util.Log
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import com.aspect.compose.inspector.core.CompositionTreeParser
import com.aspect.compose.inspector.core.RecompositionTracker
import com.aspect.compose.inspector.core.SubcompositionResolver
import com.aspect.compose.inspector.model.InspectorNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "InspectorStore"

/**
 * MVI Store that processes [InspectorIntent]s and produces [InspectorState].
 *
 * Tree parsing is performed asynchronously on [Dispatchers.Default] to avoid
 * blocking the main thread, as SlotTable traversal can be expensive (~800ms
 * for complex UIs).
 */
@OptIn(UiToolingDataApi::class)
class InspectorStore(
    private val treeParser: CompositionTreeParser = CompositionTreeParser(),
    private val subcompositionResolver: SubcompositionResolver = SubcompositionResolver(),
    private val recompositionTracker: RecompositionTracker = RecompositionTracker()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(InspectorState())
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    private var lastRootGroup: Group? = null

    fun dispatch(intent: InspectorIntent) {
        when (intent) {
            is InspectorIntent.AttachTo -> {
                lastRootGroup = intent.rootGroup
                _state.value = _state.value.copy(isLoading = true, error = null, isOverlayVisible = true)
                parseTreeAsync(intent.rootGroup)
            }

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

            is InspectorIntent.RefreshTree -> {
                val group = lastRootGroup
                if (group == null) {
                    _state.value = _state.value.copy(error = "No composition attached")
                } else {
                    _state.value = _state.value.copy(isLoading = true, error = null)
                    parseTreeAsync(group)
                }
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
        scope.cancel()
    }

    private fun parseTreeAsync(rootGroup: Group) {
        scope.launch {
            try {
                val tree = treeParser.parse(rootGroup)
                _state.value = _state.value.copy(
                    tree = tree.roots,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse composition tree", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to parse tree: ${e.message}"
                )
            }
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
