package com.aspect.compose.inspector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorTreeTest {

    // --- nodeCount ---

    @Test
    fun `empty tree has nodeCount 0`() {
        val tree = InspectorTree()

        assertEquals(0, tree.nodeCount)
    }

    @Test
    fun `tree with one root has nodeCount 1`() {
        val tree = InspectorTree(
            roots = listOf(InspectorNode(id = "1", name = "Column"))
        )

        assertEquals(1, tree.nodeCount)
    }

    @Test
    fun `tree with multiple roots counts correctly`() {
        val tree = InspectorTree(
            roots = listOf(
                InspectorNode(id = "1", name = "Column"),
                InspectorNode(id = "2", name = "Row"),
                InspectorNode(id = "3", name = "Box")
            )
        )

        assertEquals(3, tree.nodeCount)
    }

    @Test
    fun `tree with nested children counts recursively`() {
        val tree = InspectorTree(
            roots = listOf(
                InspectorNode(
                    id = "root", name = "Column",
                    children = listOf(
                        InspectorNode(id = "child-1", name = "Text"),
                        InspectorNode(id = "child-2", name = "Button")
                    )
                )
            )
        )

        assertEquals(3, tree.nodeCount)
    }

    @Test
    fun `tree with deeply nested children counts all levels`() {
        // root -> child -> grandchild -> great-grandchild
        val tree = InspectorTree(
            roots = listOf(
                InspectorNode(
                    id = "root", name = "Column",
                    children = listOf(
                        InspectorNode(
                            id = "child", name = "Row",
                            children = listOf(
                                InspectorNode(
                                    id = "grandchild", name = "Box",
                                    children = listOf(
                                        InspectorNode(id = "great-grandchild", name = "Text")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(4, tree.nodeCount)
    }

    @Test
    fun `tree with multiple roots and nested children counts all nodes`() {
        // root1 -> child1a, child1b
        // root2 -> child2a -> grandchild2a
        val tree = InspectorTree(
            roots = listOf(
                InspectorNode(
                    id = "root1", name = "Column",
                    children = listOf(
                        InspectorNode(id = "child1a", name = "Text"),
                        InspectorNode(id = "child1b", name = "Button")
                    )
                ),
                InspectorNode(
                    id = "root2", name = "Row",
                    children = listOf(
                        InspectorNode(
                            id = "child2a", name = "Box",
                            children = listOf(
                                InspectorNode(id = "grandchild2a", name = "Icon")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(6, tree.nodeCount)
    }

    // --- roots ---

    @Test
    fun `default tree has empty roots list`() {
        val tree = InspectorTree()

        assertTrue(tree.roots.isEmpty())
    }

    // --- InspectorNode defaults ---

    @Test
    fun `InspectorNode has correct default values`() {
        val node = InspectorNode(id = "test", name = "TestNode")

        assertEquals("test", node.id)
        assertEquals("TestNode", node.name)
        assertEquals(null, node.sourceLocation)
        assertTrue(node.parameters.isEmpty())
        assertEquals(null, node.bounds)
        assertEquals(0, node.recompositionCount)
        assertTrue(node.children.isEmpty())
        assertEquals(0, node.depth)
    }

    @Test
    fun `InspectorNode with all parameters set`() {
        val params = listOf(NodeParameter(name = "text", type = "String", value = "hello"))
        val children = listOf(InspectorNode(id = "child", name = "Icon"))
        val node = InspectorNode(
            id = "full",
            name = "Text",
            sourceLocation = "Main.kt:42",
            parameters = params,
            bounds = null,
            recompositionCount = 7,
            children = children,
            depth = 2
        )

        assertEquals("full", node.id)
        assertEquals("Text", node.name)
        assertEquals("Main.kt:42", node.sourceLocation)
        assertEquals(1, node.parameters.size)
        assertEquals("text", node.parameters[0].name)
        assertEquals(7, node.recompositionCount)
        assertEquals(1, node.children.size)
        assertEquals(2, node.depth)
    }

    // --- NodeParameter ---

    @Test
    fun `NodeParameter has correct default value`() {
        val param = NodeParameter(name = "color", type = "Color")

        assertEquals("color", param.name)
        assertEquals("Color", param.type)
        assertEquals(null, param.value)
    }

    @Test
    fun `NodeParameter with value set`() {
        val param = NodeParameter(name = "text", type = "String", value = "Hello")

        assertEquals("Hello", param.value)
    }
}
