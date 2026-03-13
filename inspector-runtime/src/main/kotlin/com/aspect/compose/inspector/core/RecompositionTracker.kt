package com.aspect.compose.inspector.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks recomposition counts per composable node.
 */
class RecompositionTracker {

    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun onRecomposition(nodeId: String) {
        counts.getOrPut(nodeId) { AtomicInteger(0) }.incrementAndGet()
    }

    fun getCount(nodeId: String): Int = counts[nodeId]?.get() ?: 0

    fun getAllCounts(): Map<String, Int> = counts.mapValues { it.value.get() }

    fun reset() {
        counts.clear()
    }
}
