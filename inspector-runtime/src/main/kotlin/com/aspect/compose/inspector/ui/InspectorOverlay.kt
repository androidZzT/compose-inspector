package com.aspect.compose.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspect.compose.inspector.model.InspectorTree
import com.aspect.compose.inspector.mvi.InspectorState

/**
 * The main Inspector panel that hosts TreeView and NodeDetailPanel.
 * Slides up from the bottom as a half-screen overlay.
 */
@Composable
fun InspectorOverlay(
    state: InspectorState,
    onNodeClick: (String) -> Unit,
    onNodeExpand: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            InspectorToolbar(
                nodeCount = InspectorTree(state.tree).nodeCount,
                onRefresh = onRefresh,
                onClose = onDismiss
            )

            HorizontalDivider()

            // Content area
            if (state.isLoading) {
                LoadingView()
            } else if (state.error != null) {
                ErrorView(state.error)
            } else if (state.tree.isEmpty()) {
                EmptyView()
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    // Tree view - top half
                    Box(modifier = Modifier.weight(1f)) {
                        TreeView(
                            nodes = state.tree,
                            expandedNodeIds = state.expandedNodeIds,
                            selectedNodeId = state.selectedNode?.id,
                            onNodeClick = onNodeClick,
                            onNodeExpand = onNodeExpand
                        )
                    }

                    // Node detail - bottom portion (if a node is selected)
                    state.selectedNode?.let { node ->
                        HorizontalDivider()
                        Box(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxWidth()
                        ) {
                            NodeDetailPanel(node = node)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorToolbar(
    nodeCount: Int,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Compose Inspector",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "$nodeCount nodes",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Refresh button — no ripple to avoid "Cannot start animator on detached view"
        // crash when the overlay window is removed while ripple animation is active
        ToolbarButton(text = "\u21BB", fontSize = 18.sp, onClick = onRefresh)

        // Close button
        ToolbarButton(text = "\u2715", fontSize = 16.sp, onClick = onClose)
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Parsing composition tree...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ErrorView(error: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EmptyView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No composition data available.\nTap refresh to scan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

/**
 * Toolbar button without native ripple indication.
 *
 * Using [IconButton] with native ripple causes "Cannot start this animator on a
 * detached view" crash on Samsung devices when the overlay window is removed while
 * a ripple animation is still active. This button uses indication = null to avoid it.
 */
@Composable
private fun ToolbarButton(
    text: String,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = fontSize)
    }
}
