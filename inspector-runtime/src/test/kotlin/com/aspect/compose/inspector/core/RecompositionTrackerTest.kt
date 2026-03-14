package com.aspect.compose.inspector.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class RecompositionTrackerTest {

    private lateinit var tracker: RecompositionTracker

    @Before
    fun setUp() {
        tracker = RecompositionTracker()
    }

    @Test
    fun `getCount returns 0 for unknown nodeId`() {
        assertEquals(0, tracker.getCount("unknown"))
    }

    @Test
    fun `onRecomposition increments count to 1`() {
        tracker.onRecomposition("node-1")

        assertEquals(1, tracker.getCount("node-1"))
    }

    @Test
    fun `onRecomposition accumulates count correctly`() {
        repeat(5) { tracker.onRecomposition("node-1") }

        assertEquals(5, tracker.getCount("node-1"))
    }

    @Test
    fun `onRecomposition tracks multiple nodes independently`() {
        tracker.onRecomposition("node-a")
        tracker.onRecomposition("node-a")
        tracker.onRecomposition("node-b")

        assertEquals(2, tracker.getCount("node-a"))
        assertEquals(1, tracker.getCount("node-b"))
    }

    @Test
    fun `getAllCounts returns complete mapping`() {
        tracker.onRecomposition("node-1")
        tracker.onRecomposition("node-1")
        tracker.onRecomposition("node-2")
        tracker.onRecomposition("node-3")
        tracker.onRecomposition("node-3")
        tracker.onRecomposition("node-3")

        val allCounts = tracker.getAllCounts()

        assertEquals(3, allCounts.size)
        assertEquals(2, allCounts["node-1"])
        assertEquals(1, allCounts["node-2"])
        assertEquals(3, allCounts["node-3"])
    }

    @Test
    fun `getAllCounts returns empty map when no recompositions tracked`() {
        assertTrue(tracker.getAllCounts().isEmpty())
    }

    @Test
    fun `reset clears all counts`() {
        tracker.onRecomposition("node-1")
        tracker.onRecomposition("node-2")

        tracker.reset()

        assertEquals(0, tracker.getCount("node-1"))
        assertEquals(0, tracker.getCount("node-2"))
        assertTrue(tracker.getAllCounts().isEmpty())
    }

    @Test
    fun `reset followed by new recomposition starts fresh`() {
        tracker.onRecomposition("node-1")
        tracker.onRecomposition("node-1")
        tracker.reset()

        tracker.onRecomposition("node-1")

        assertEquals(1, tracker.getCount("node-1"))
    }

    @Test
    fun `concurrent recompositions on same nodeId are thread-safe`() {
        val threadCount = 10
        val iterationsPerThread = 1000
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(iterationsPerThread) {
                        tracker.onRecomposition("shared-node")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(threadCount * iterationsPerThread, tracker.getCount("shared-node"))
    }

    @Test
    fun `concurrent recompositions on different nodeIds are thread-safe`() {
        val threadCount = 10
        val iterationsPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { threadIndex ->
            executor.submit {
                try {
                    repeat(iterationsPerThread) {
                        tracker.onRecomposition("node-$threadIndex")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val allCounts = tracker.getAllCounts()
        assertEquals(threadCount, allCounts.size)
        allCounts.values.forEach { count ->
            assertEquals(iterationsPerThread, count)
        }
    }

    // ---- recordFingerprint tests (TDD — red light) ----

    @Test
    fun `recordFingerprint - first call does not increment count`() {
        tracker.recordFingerprint("node-1", fingerprint = 12345)

        assertEquals(0, tracker.getCount("node-1"))
    }

    @Test
    fun `recordFingerprint - same fingerprint does not increment count`() {
        tracker.recordFingerprint("node-1", fingerprint = 100)
        tracker.recordFingerprint("node-1", fingerprint = 100)
        tracker.recordFingerprint("node-1", fingerprint = 100)

        assertEquals(0, tracker.getCount("node-1"))
    }

    @Test
    fun `recordFingerprint - different fingerprint increments count`() {
        tracker.recordFingerprint("node-1", fingerprint = 100)
        tracker.recordFingerprint("node-1", fingerprint = 200)

        assertEquals(1, tracker.getCount("node-1"))
    }

    @Test
    fun `recordFingerprint - multiple changes accumulate count`() {
        tracker.recordFingerprint("node-1", fingerprint = 1)
        tracker.recordFingerprint("node-1", fingerprint = 2)  // +1
        tracker.recordFingerprint("node-1", fingerprint = 2)  // same, no increment
        tracker.recordFingerprint("node-1", fingerprint = 3)  // +1
        tracker.recordFingerprint("node-1", fingerprint = 1)  // +1 (back to original)

        assertEquals(3, tracker.getCount("node-1"))
    }

    @Test
    fun `recordFingerprint - independent nodes track separately`() {
        tracker.recordFingerprint("node-a", fingerprint = 1)
        tracker.recordFingerprint("node-b", fingerprint = 10)

        tracker.recordFingerprint("node-a", fingerprint = 2)  // node-a changes
        tracker.recordFingerprint("node-b", fingerprint = 10) // node-b stays same

        assertEquals(1, tracker.getCount("node-a"))
        assertEquals(0, tracker.getCount("node-b"))
    }

    @Test
    fun `reset clears both counts and fingerprints`() {
        tracker.recordFingerprint("node-1", fingerprint = 1)
        tracker.recordFingerprint("node-1", fingerprint = 2)  // count = 1
        assertEquals(1, tracker.getCount("node-1"))

        tracker.reset()

        assertEquals(0, tracker.getCount("node-1"))
        // After reset, recording same fingerprint as before should be treated as first call
        tracker.recordFingerprint("node-1", fingerprint = 2)
        assertEquals(0, tracker.getCount("node-1"))
    }

    @Test
    fun `recordFingerprint - concurrent fingerprint updates are thread-safe`() {
        val threadCount = 10
        val iterationsPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        // Each thread records a unique node with alternating fingerprints
        repeat(threadCount) { threadIndex ->
            executor.submit {
                try {
                    repeat(iterationsPerThread) { i ->
                        tracker.recordFingerprint(
                            "node-$threadIndex",
                            fingerprint = i % 2  // alternates 0, 1, 0, 1...
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Each node should have count = iterationsPerThread - 1
        // (first call doesn't count, every subsequent call changes fingerprint)
        val allCounts = tracker.getAllCounts()
        assertEquals(threadCount, allCounts.size)
        allCounts.forEach { (_, count) ->
            assertEquals(iterationsPerThread - 1, count)
        }
    }
}
