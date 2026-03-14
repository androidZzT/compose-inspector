package com.aspect.compose.inspector.mvi

import com.aspect.compose.inspector.model.InspectorNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InspectorStoreTest {

    private lateinit var store: InspectorStore

    @Before
    fun setUp() {
        store = InspectorStore()
    }

    // --- Initial State ---

    @Test
    fun `initial state has empty tree`() {
        assertTrue(store.state.value.tree.isEmpty())
    }

    @Test
    fun `initial state has no selected node`() {
        assertNull(store.state.value.selectedNode)
    }

    @Test
    fun `initial state has no expanded nodes`() {
        assertTrue(store.state.value.expandedNodeIds.isEmpty())
    }

    @Test
    fun `initial state has overlay hidden`() {
        assertFalse(store.state.value.isOverlayVisible)
    }

    @Test
    fun `initial state is not loading`() {
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun `initial state has no error`() {
        assertNull(store.state.value.error)
    }

    // --- SelectNode ---

    @Test
    fun `SelectNode selects matching node from tree`() {
        val targetNode = InspectorNode(id = "node-2", name = "Text")
        val tree = listOf(
            InspectorNode(id = "node-1", name = "Column"),
            targetNode
        )
        store = storeWithTree(tree)

        store.dispatch(InspectorIntent.SelectNode("node-2"))

        assertNotNull(store.state.value.selectedNode)
        assertEquals("node-2", store.state.value.selectedNode?.id)
        assertEquals("Text", store.state.value.selectedNode?.name)
    }

    @Test
    fun `SelectNode finds nested child node`() {
        val childNode = InspectorNode(id = "child-1", name = "Icon")
        val tree = listOf(
            InspectorNode(
                id = "parent-1",
                name = "Row",
                children = listOf(childNode)
            )
        )
        store = storeWithTree(tree)

        store.dispatch(InspectorIntent.SelectNode("child-1"))

        assertNotNull(store.state.value.selectedNode)
        assertEquals("child-1", store.state.value.selectedNode?.id)
    }

    @Test
    fun `SelectNode finds deeply nested node`() {
        val deepNode = InspectorNode(id = "deep", name = "Text")
        val tree = listOf(
            InspectorNode(
                id = "l1", name = "Column",
                children = listOf(
                    InspectorNode(
                        id = "l2", name = "Row",
                        children = listOf(
                            InspectorNode(
                                id = "l3", name = "Box",
                                children = listOf(deepNode)
                            )
                        )
                    )
                )
            )
        )
        store = storeWithTree(tree)

        store.dispatch(InspectorIntent.SelectNode("deep"))

        assertEquals("deep", store.state.value.selectedNode?.id)
    }

    @Test
    fun `SelectNode returns null for non-existent nodeId`() {
        val tree = listOf(InspectorNode(id = "node-1", name = "Box"))
        store = storeWithTree(tree)

        store.dispatch(InspectorIntent.SelectNode("non-existent"))

        assertNull(store.state.value.selectedNode)
    }

    @Test
    fun `SelectNode on empty tree returns null`() {
        store.dispatch(InspectorIntent.SelectNode("any-id"))

        assertNull(store.state.value.selectedNode)
    }

    @Test
    fun `SelectNode replaces previously selected node`() {
        val tree = listOf(
            InspectorNode(id = "node-a", name = "Column"),
            InspectorNode(id = "node-b", name = "Row")
        )
        store = storeWithTree(tree)

        store.dispatch(InspectorIntent.SelectNode("node-a"))
        assertEquals("node-a", store.state.value.selectedNode?.id)

        store.dispatch(InspectorIntent.SelectNode("node-b"))
        assertEquals("node-b", store.state.value.selectedNode?.id)
    }

    // --- ToggleExpand ---

    @Test
    fun `ToggleExpand adds nodeId to expanded set`() {
        store.dispatch(InspectorIntent.ToggleExpand("node-1"))

        assertTrue(store.state.value.expandedNodeIds.contains("node-1"))
    }

    @Test
    fun `ToggleExpand twice removes nodeId from expanded set`() {
        store.dispatch(InspectorIntent.ToggleExpand("node-1"))
        store.dispatch(InspectorIntent.ToggleExpand("node-1"))

        assertFalse(store.state.value.expandedNodeIds.contains("node-1"))
    }

    @Test
    fun `ToggleExpand multiple nodes independently`() {
        store.dispatch(InspectorIntent.ToggleExpand("node-1"))
        store.dispatch(InspectorIntent.ToggleExpand("node-2"))

        assertTrue(store.state.value.expandedNodeIds.contains("node-1"))
        assertTrue(store.state.value.expandedNodeIds.contains("node-2"))
        assertEquals(2, store.state.value.expandedNodeIds.size)
    }

    @Test
    fun `ToggleExpand collapse one while keeping others expanded`() {
        store.dispatch(InspectorIntent.ToggleExpand("node-1"))
        store.dispatch(InspectorIntent.ToggleExpand("node-2"))
        store.dispatch(InspectorIntent.ToggleExpand("node-1"))

        assertFalse(store.state.value.expandedNodeIds.contains("node-1"))
        assertTrue(store.state.value.expandedNodeIds.contains("node-2"))
    }

    // --- ToggleOverlay ---

    @Test
    fun `ToggleOverlay makes overlay visible`() {
        store.dispatch(InspectorIntent.ToggleOverlay)

        assertTrue(store.state.value.isOverlayVisible)
    }

    @Test
    fun `ToggleOverlay twice hides overlay`() {
        store.dispatch(InspectorIntent.ToggleOverlay)
        store.dispatch(InspectorIntent.ToggleOverlay)

        assertFalse(store.state.value.isOverlayVisible)
    }

    // --- Helpers ---

    private fun storeWithTree(tree: List<InspectorNode>): InspectorStore {
        return InspectorStore().also { s ->
            val stateField = InspectorStore::class.java.getDeclaredField("_state")
            stateField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val stateFlow = stateField.get(s) as kotlinx.coroutines.flow.MutableStateFlow<InspectorState>
            stateFlow.value = InspectorState(tree = tree)
        }
    }
}
