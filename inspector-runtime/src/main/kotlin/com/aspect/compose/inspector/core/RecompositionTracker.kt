package com.aspect.compose.inspector.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks recomposition counts per composable node.
 */
class RecompositionTracker {

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val fingerprints = ConcurrentHashMap<String, Int>()

    fun onRecomposition(nodeId: String) {
        counts.getOrPut(nodeId) { AtomicInteger(0) }.incrementAndGet()
    }

    /**
     * Records a content fingerprint for the given node.
     * If the fingerprint differs from the previously recorded value,
     * the recomposition count is incremented.
     * First call for a node only stores the fingerprint without incrementing.
     */
    fun recordFingerprint(stableId: String, fingerprint: Int) {
        fingerprints.compute(stableId) { _, previous ->
            if (previous != null && previous != fingerprint) {
                counts.getOrPut(stableId) { AtomicInteger(0) }.incrementAndGet()
            }
            fingerprint
        }
    }

    fun getCount(nodeId: String): Int = counts[nodeId]?.get() ?: 0

    fun getAllCounts(): Map<String, Int> = counts.mapValues { it.value.get() }

    fun reset() {
        counts.clear()
        fingerprints.clear()
    }
}
