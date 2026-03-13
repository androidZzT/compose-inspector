package com.aspect.compose.inspector.model

import androidx.compose.runtime.Immutable

/**
 * Represents a parameter of a Compose node.
 */
@Immutable
data class NodeParameter(
    val name: String,
    val type: String,
    val value: Any? = null
)
