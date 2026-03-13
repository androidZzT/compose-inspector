package com.aspect.compose.inspector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspect.compose.inspector.model.InspectorNode
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
    AnimatedVisibility(
        visible = state.isOverlayVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Toolbar
                InspectorToolbar(
                    nodeCount = state.tree.size,
                    onRefresh = onRefresh,
                    onClose = onDismiss
                )

                HorizontalDivider()

                // Content area
                if (state.error != null) {
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

        // Refresh button
        IconButton(onClick = onRefresh) {
            Text(text = "\u21BB", fontSize = 18.sp)
        }

        // Close button
        IconButton(onClick = onClose) {
            Text(text = "\u2715", fontSize = 16.sp)
        }
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
