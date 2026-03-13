package com.aspect.compose.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspect.compose.inspector.model.InspectorNode

/**
 * Displays detailed information about a selected node,
 * including parameters, bounds, and recomposition count.
 */
@Composable
fun NodeDetailPanel(
    node: InspectorNode
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Node name
        Text(
            text = node.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Source location
        node.sourceLocation?.let { location ->
            Text(
                text = location,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Recomposition count
        SectionHeader("Recomposition")
        DetailRow("Count", "${node.recompositionCount}")

        Spacer(modifier = Modifier.height(8.dp))

        // Bounds
        node.bounds?.let { bounds ->
            SectionHeader("Layout")
            DetailRow("Position", "(${bounds.left}, ${bounds.top})")
            DetailRow("Size", "${bounds.width} x ${bounds.height}")

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Parameters
        if (node.parameters.isNotEmpty()) {
            SectionHeader("Parameters")
            for (param in node.parameters) {
                DetailRow(
                    label = "${param.name}: ${param.type}",
                    value = param.value?.toString() ?: "null"
                )
            }
        }

        // Children count
        if (node.children.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Children")
            DetailRow("Count", "${node.children.size}")
        }

        // Depth
        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader("Hierarchy")
        DetailRow("Depth", "${node.depth}")
        DetailRow("ID", node.id)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
