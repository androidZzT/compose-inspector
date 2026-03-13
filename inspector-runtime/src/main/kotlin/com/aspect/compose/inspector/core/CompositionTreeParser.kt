package com.aspect.compose.inspector.core

import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.NodeGroup
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.unit.IntRect
import com.aspect.compose.inspector.model.InspectorNode
import com.aspect.compose.inspector.model.InspectorTree
import com.aspect.compose.inspector.model.NodeParameter

/**
 * Parses Compose SlotTable into an [InspectorTree] using the tooling data API.
 *
 * Traverses [Group] hierarchy from [CompositionData.asTree()], filters to only
 * meaningful UI nodes, and maps them into [InspectorNode] tree.
 */
@OptIn(UiToolingDataApi::class)
class CompositionTreeParser(
    private val recompositionTracker: RecompositionTracker? = null
) {

    private var idCounter = 0

    fun parse(rootGroup: Group): InspectorTree {
        idCounter = 0
        val roots = parseGroup(rootGroup, depth = 0)
        return InspectorTree(roots = roots)
    }

    private fun parseGroup(group: Group, depth: Int): List<InspectorNode> {
        val isNodeGroup = group is NodeGroup
        val name = extractName(group)
        val hasLayoutNode = group.modifierInfo.isNotEmpty() || isNodeGroup

        if (hasLayoutNode && name.isNotEmpty()) {
            val nodeId = generateId(name, depth)
            val node = InspectorNode(
                id = nodeId,
                name = name,
                sourceLocation = extractSourceLocation(group),
                parameters = extractParameters(group),
                bounds = extractBounds(group),
                recompositionCount = recompositionTracker?.getCount(nodeId) ?: 0,
                children = group.children.flatMap { parseGroup(it, depth + 1) },
                depth = depth
            )
            return listOf(node)
        }

        // Not a meaningful node — pass through to children
        return group.children.flatMap { parseGroup(it, depth) }
    }

    private fun extractName(group: Group): String {
        // Use the group name which corresponds to the composable function name
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

    private fun extractBounds(group: Group): IntRect {
        val box = group.box
        return IntRect(
            left = box.left,
            top = box.top,
            right = box.right,
            bottom = box.bottom
        )
    }

    private fun generateId(name: String, depth: Int): String {
        return "${name}_${depth}_${idCounter++}"
    }

    private fun Any.toDisplayString(): String {
        return when (this) {
            is String -> "\"$this\""
            is Enum<*> -> this.name
            else -> toString()
        }
    }
}
