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
}
