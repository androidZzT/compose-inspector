package com.aspect.compose.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspect.compose.inspector.model.InspectorNode

/**
 * Displays the Compose view tree as an expandable list.
 */
@Composable
fun TreeView(
    nodes: List<InspectorNode>,
    expandedNodeIds: Set<String>,
    selectedNodeId: String?,
    onNodeClick: (String) -> Unit,
    onNodeExpand: (String) -> Unit
) {
    val flattenedNodes = remember(nodes, expandedNodeIds) {
        flattenTree(nodes, expandedNodeIds)
    }

    LazyColumn {
        items(flattenedNodes, key = { it.id }) { node ->
            TreeNodeRow(
                node = node,
                isExpanded = node.id in expandedNodeIds,
                isSelected = node.id == selectedNodeId,
                hasChildren = node.children.isNotEmpty(),
                onNodeClick = { onNodeClick(node.id) },
                onExpandClick = { onNodeExpand(node.id) }
            )
        }
    }
}

@Composable
private fun TreeNodeRow(
    node: InspectorNode,
    isExpanded: Boolean,
    isSelected: Boolean,
    hasChildren: Boolean,
    onNodeClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onNodeClick)
            .padding(start = (node.depth * 16).dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse indicator
        if (hasChildren) {
            Text(
                text = if (isExpanded) "\u25BC" else "\u25B6",
                fontSize = 10.sp,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onExpandClick)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Node name
        Text(
            text = node.name,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Recomposition count badge
        if (node.recompositionCount > 0) {
            val badgeColor = when {
                node.recompositionCount > 10 -> Color(0xFFE53935) // Red
                node.recompositionCount > 3 -> Color(0xFFFFA726)  // Orange
                else -> Color(0xFF66BB6A)                          // Green
            }
            Text(
                text = "${node.recompositionCount}",
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .background(badgeColor, MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * Flattens the tree into a list respecting expanded/collapsed state.
 */
private fun flattenTree(
    nodes: List<InspectorNode>,
    expandedIds: Set<String>
): List<InspectorNode> {
    val result = mutableListOf<InspectorNode>()
    for (node in nodes) {
        result.add(node)
        if (node.id in expandedIds && node.children.isNotEmpty()) {
            result.addAll(flattenTree(node.children, expandedIds))
        }
    }
    return result
}
