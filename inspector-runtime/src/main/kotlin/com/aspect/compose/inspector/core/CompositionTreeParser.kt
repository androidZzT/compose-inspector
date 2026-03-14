package com.aspect.compose.inspector.core

import android.util.Log
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.NodeGroup
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.unit.IntRect
import com.aspect.compose.inspector.model.InspectorNode
import com.aspect.compose.inspector.model.InspectorTree
import com.aspect.compose.inspector.model.NodeParameter

private const val TAG = "CompositionTreeParser"

/**
 * Parses Compose SlotTable into an [InspectorTree] using the tooling data API.
 *
 * Traverses [Group] hierarchy from [CompositionData.asTree()], filters to only
 * meaningful UI nodes, and maps them into [InspectorNode] tree.
 */
@OptIn(UiToolingDataApi::class)
class CompositionTreeParser(
    private val recompositionTracker: RecompositionTracker? = null,
    private val subcompositionResolver: SubcompositionResolver? = null
) {

    fun parse(rootGroup: Group): InspectorTree {
        val roots = parseGroup(rootGroup, depth = 0, siblingIndex = 0)

        // Also parse subcompositions if resolver is available
        val subRoots = subcompositionResolver?.let { resolver ->
            try {
                val subs = resolver.findSubcompositions(rootGroup)
                subs.flatMap { subGroup ->
                    parseGroup(subGroup, depth = 0, siblingIndex = 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve subcompositions", e)
                emptyList()
            }
        } ?: emptyList()

        return InspectorTree(roots = roots + subRoots)
    }

    private fun parseGroup(group: Group, depth: Int, siblingIndex: Int): List<InspectorNode> {
        val isNodeGroup = group is NodeGroup
        val name = extractName(group)
        val hasLayoutNode = group.modifierInfo.isNotEmpty() || isNodeGroup

        if (hasLayoutNode && name.isNotEmpty()) {
            val nodeId = generateStableId(group, name, depth, siblingIndex)
            val children = mutableListOf<InspectorNode>()
            group.children.forEachIndexed { index, child ->
                children.addAll(parseGroup(child, depth + 1, index))
            }
            val node = InspectorNode(
                id = nodeId,
                name = name,
                sourceLocation = extractSourceLocation(group),
                parameters = extractParameters(group),
                bounds = extractBounds(group),
                recompositionCount = recompositionTracker?.getCount(nodeId) ?: 0,
                children = children,
                depth = depth
            )
            return listOf(node)
        }

        // Not a meaningful node — pass through to children
        val result = mutableListOf<InspectorNode>()
        group.children.forEachIndexed { index, child ->
            result.addAll(parseGroup(child, depth, index))
        }
        return result
    }

    private fun extractName(group: Group): String {
        return group.name ?: ""
    }

    private fun extractSourceLocation(group: Group): String? {
        val location = group.location ?: return null
        val file = location.sourceFile ?: return null
        return "$file:${location.lineNumber}"
    }

    private fun extractParameters(group: Group): List<NodeParameter> {
        return group.parameters.map { param ->
            NodeParameter(
                name = param.name,
                type = param.value?.javaClass?.simpleName ?: "Unknown",
                value = param.value?.toDisplayString()
            )
        }
    }

    private fun extractBounds(group: Group): IntRect? {
        val box = group.box
        // Return null for zero-area bounds (no layout info)
        return if (box.left == 0 && box.top == 0 && box.right == 0 && box.bottom == 0) {
            null
        } else {
            IntRect(left = box.left, top = box.top, right = box.right, bottom = box.bottom)
        }
    }

    /**
     * Generates a stable ID based on Group's own properties.
     * Uses key, name, depth, and sibling index to produce consistent IDs
     * across multiple parse passes of the same tree structure.
     */
    private fun generateStableId(group: Group, name: String, depth: Int, siblingIndex: Int): String {
        val key = group.key?.hashCode() ?: 0
        val locationHash = group.location?.let {
            "${it.sourceFile}:${it.lineNumber}".hashCode()
        } ?: 0
        return "${name}_d${depth}_s${siblingIndex}_k${key}_l${locationHash}"
    }

    private fun Any.toDisplayString(): String {
        return when (this) {
            is String -> "\"$this\""
            is Enum<*> -> this.name
            else -> toString()
        }
    }
}
