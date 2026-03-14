package com.aspect.compose.inspector.core

import android.util.Log
import android.view.View
import androidx.compose.ui.unit.IntRect
import com.aspect.compose.inspector.model.InspectorNode
import com.aspect.compose.inspector.model.InspectorTree
import com.aspect.compose.inspector.model.NodeParameter

private const val TAG = "LayoutNodeTreeParser"

/**
 * Parses the Compose LayoutNode tree from AndroidComposeView via reflection.
 *
 * Walks the LayoutNode tree directly (the actual layout/render tree used by Compose),
 * extracting:
 * - Node type from measurePolicy class names
 * - Text content from semantics configuration
 * - Modifier details (padding, size, background, etc.) from the Modifier.Node chain
 * - Layout bounds from NodeCoordinator measurements
 */
class LayoutNodeTreeParser {

    fun parse(composeView: View): InspectorTree {
        try {
            val rootLayoutNode = getRootLayoutNode(composeView) ?: run {
                Log.e(TAG, "Could not get root LayoutNode from ${composeView.javaClass.name}")
                return InspectorTree(emptyList())
            }
            val children = getChildren(rootLayoutNode)
            val roots = children.mapIndexed { index, child ->
                parseLayoutNode(child, depth = 0, siblingIndex = index)
            }
            return InspectorTree(roots)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LayoutNode tree", e)
            return InspectorTree(emptyList())
        }
    }

    private fun parseLayoutNode(node: Any, depth: Int, siblingIndex: Int): InspectorNode {
        val baseName = extractNodeName(node)

        val textContent = extractTextFromSemantics(node)
        val bounds = extractBounds(node)
        val modifiers = extractModifierChain(node)
        val children = getChildren(node)

        // Enrich the display name with text content
        val displayName = when {
            textContent != null && baseName == "Text" -> {
                val truncated = textContent.take(30)
                "Text(\"$truncated\"${if (textContent.length > 30) "…" else ""})"
            }
            textContent != null -> "$baseName(\"${textContent.take(20)}\")"
            else -> baseName
        }

        // Add text content as a parameter too
        val allParams = if (textContent != null) {
            listOf(NodeParameter("text", "String", "\"$textContent\"")) + modifiers
        } else {
            modifiers
        }

        val childNodes = children.mapIndexed { index, child ->
            parseLayoutNode(child, depth + 1, index)
        }

        val id = "${baseName}_d${depth}_s${siblingIndex}_${node.hashCode()}"

        return InspectorNode(
            id = id,
            name = displayName,
            sourceLocation = null,
            parameters = allParams,
            bounds = bounds,
            recompositionCount = 0,
            children = childNodes,
            depth = depth
        )
    }

    // ---- Text content extraction via semantics ----

    /**
     * Extracts text content from the node's semantics configuration.
     *
     * In Compose, text content is stored in `SemanticsProperties.Text` within the
     * `SemanticsConfiguration` attached to semantic modifier nodes. We walk the
     * modifier node chain, find semantics nodes, and extract the text property.
     */
    private fun extractTextFromSemantics(node: Any): String? {
        try {
            val head = getModifierNodeHead(node) ?: return null
            var current: Any? = head
            while (current != null) {
                val className = current.javaClass.name

                // Check for TextStringSimpleNode / TextAnnotatedStringNode
                // which store text as a direct "text" field.
                // Runtime class name is "TextStringSimpleNode" (not "Element").
                if (className.contains("TextStringSimple") || className.contains("TextAnnotatedString")) {
                    val text = extractTextFromTextElement(current)
                    if (text != null) return text
                }

                // Check for SemanticsModifierNode by interface (class name may not contain "Semantics")
                val implementsSemantics = current.javaClass.interfaces.any {
                    it.name.contains("SemanticsModifierNode")
                }
                if (implementsSemantics) {
                    val text = extractTextFromSemanticsNode(current)
                    if (text != null) return text
                }

                current = getNextModifierNode(current)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract text from semantics", e)
        }
        return null
    }

    private fun extractTextFromSemanticsNode(semanticsNode: Any): String? {
        try {
            // First try direct "text" field (TextStringSimpleNode stores it directly)
            val directText = extractTextFromTextElement(semanticsNode)
            if (directText != null) return directText

            // Then try SemanticsConfiguration
            for (field in getAllDeclaredFields(semanticsNode.javaClass)) {
                if (field.name.contains("semanticsConfiguration") ||
                    field.name.contains("config") ||
                    field.name == "mergedConfig"
                ) {
                    field.isAccessible = true
                    val config = field.get(semanticsNode) ?: continue
                    val text = extractTextFromConfig(config)
                    if (text != null) return text
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract text from semantics node", e)
        }
        return null
    }

    private fun extractTextFromConfig(config: Any): String? {
        try {
            // SemanticsConfiguration is Iterable<Map.Entry<SemanticsPropertyKey<*>, Any?>>
            if (config is Iterable<*>) {
                for (entry in config) {
                    if (entry == null) continue
                    try {
                        // Get the key from the entry
                        val key = try {
                            val kf = entry.javaClass.declaredFields.find { it.name == "key" }
                            if (kf != null) {
                                kf.isAccessible = true
                                kf.get(entry)
                            } else {
                                val getKey = entry.javaClass.getMethod("getKey")
                                getKey.invoke(entry)
                            }
                        } catch (_: Exception) { null }

                        if (key != null) {
                            val keyName = try {
                                val nameField = key.javaClass.getDeclaredField("name")
                                nameField.isAccessible = true
                                nameField.get(key) as? String
                            } catch (_: Exception) { key.toString() }

                            if (keyName == "Text") {
                                val value = try {
                                    val valueField = entry.javaClass.declaredFields.find { it.name == "value" }
                                    if (valueField != null) {
                                        valueField.isAccessible = true
                                        valueField.get(entry)
                                    } else {
                                        val getValue = entry.javaClass.getMethod("getValue")
                                        getValue.invoke(entry)
                                    }
                                } catch (_: Exception) { null }

                                if (value is List<*> && value.isNotEmpty()) {
                                    // List<AnnotatedString> — get the first one's text
                                    val first = value[0] ?: return null
                                    return try {
                                        val textField = first.javaClass.getDeclaredField("text")
                                        textField.isAccessible = true
                                        textField.get(first) as? String
                                    } catch (_: Exception) {
                                        first.toString()
                                    }
                                }
                                if (value != null) return value.toString()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Extract text from TextStringSimpleElement which stores text as a direct field.
     */
    private fun extractTextFromTextElement(element: Any): String? {
        try {
            for (field in element.javaClass.declaredFields) {
                if (field.name == "text" || field.name == "value") {
                    field.isAccessible = true
                    val value = field.get(element)
                    if (value is String) return value
                    if (value != null) {
                        // AnnotatedString — get .text field
                        try {
                            val textField = value.javaClass.getDeclaredField("text")
                            textField.isAccessible = true
                            return textField.get(value) as? String
                        } catch (_: Exception) {}
                        return value.toString()
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ---- Modifier chain extraction ----

    /**
     * Walks the Modifier.Node chain and extracts structured parameters
     * from each recognized modifier (padding, size, background, clickable, etc.).
     */
    private fun extractModifierChain(node: Any): List<NodeParameter> {
        val params = mutableListOf<NodeParameter>()
        try {
            val head = getModifierNodeHead(node) ?: return params
            var current: Any? = head
            while (current != null) {
                try {
                    extractModifierNodeParams(current, params)
                } catch (_: Exception) {}
                current = getNextModifierNode(current)
            }
        } catch (_: Exception) {}
        return params
    }

    private fun extractModifierNodeParams(modNode: Any, params: MutableList<NodeParameter>) {
        val className = modNode.javaClass.name
        when {
            // Padding
            className.containsAny("PaddingModifier", "PaddingElement", "PaddingNode", "PaddingValues") -> {
                extractPaddingParams(modNode, params)
            }
            // Size constraints
            className.containsAny("SizeModifier", "SizeElement", "SizeNode") -> {
                extractSizeParams(modNode, params)
            }
            // Fill
            className.containsAny("FillModifier", "FillElement", "FillNode") -> {
                extractFillParams(modNode, params)
            }
            // Background
            className.containsAny("BackgroundModifier", "BackgroundElement", "BackgroundNode") -> {
                extractBackgroundParams(modNode, params)
            }
            // Clip / Shape
            className.containsAny("ClipModifier", "ClipElement", "ClipNode") -> {
                extractClipParams(modNode, params)
            }
            // Alpha / GraphicsLayer
            className.containsAny("AlphaModifier", "GraphicsLayerModifier", "GraphicsLayerElement") -> {
                extractGraphicsLayerParams(modNode, params)
            }
            // Clickable
            className.containsAny("ClickableElement", "ClickableNode", "clickable") -> {
                params.add(NodeParameter("clickable", "Boolean", "true"))
            }
            // Shadow / Elevation
            className.containsAny("ShadowModifier", "ShadowElement") -> {
                extractDpField(modNode, "elevation", "shadow.elevation", params)
            }
            // Border
            className.containsAny("BorderModifier", "BorderElement") -> {
                extractBorderParams(modNode, params)
            }
            // Offset
            className.containsAny("OffsetModifier", "OffsetElement", "OffsetNode") -> {
                extractDpField(modNode, "x", "offset.x", params)
                extractDpField(modNode, "y", "offset.y", params)
            }
            // Scroll
            className.containsAny("ScrollModifier", "ScrollElement", "Scroll") -> {
                params.add(NodeParameter("scrollable", "Boolean", "true"))
            }
            // Skip internal/framework nodes that add noise
            className.containsAny(
                "NodeChain", "Tail", "SentinelHead", "LayoutAware",
                "FocusTarget", "FocusProperties", "KeyInput", "RotaryInput",
                "PointerInput", "DragAndDrop"
            ) -> { /* skip */ }
            // Unknown modifier — add simplified name for visibility
            else -> {
                val simpleName = simplifyModifierClassName(className)
                if (simpleName.isNotEmpty() && simpleName != "Node") {
                    params.add(NodeParameter(simpleName, "Modifier", null))
                }
            }
        }
    }

    // ---- Specific modifier extractors ----

    private fun extractPaddingParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        val values = mutableMapOf<String, String>()

        for (field in fields) {
            val name = field.name.lowercase()
            if (name in listOf("start", "end", "top", "bottom", "left", "right",
                    "horizontal", "vertical", "all")
            ) {
                try {
                    field.isAccessible = true
                    val dp = formatDpValue(field.getFloat(modNode))
                    if (dp != "0.dp") {
                        values[field.name] = dp
                    }
                } catch (_: Exception) {}
            }
        }

        if (values.isNotEmpty()) {
            val display = values.entries.joinToString(", ") { "${it.key}=${it.value}" }
            params.add(NodeParameter("padding", "Dp", display))
        } else {
            params.add(NodeParameter("padding", "Modifier", null))
        }
    }

    private fun extractSizeParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        for (field in fields) {
            val name = field.name.lowercase()
            when {
                name.contains("minwidth") || name.contains("min_width") -> {
                    extractDpFieldDirect(modNode, field, "size.minWidth", params)
                }
                name.contains("maxwidth") || name.contains("max_width") -> {
                    extractDpFieldDirect(modNode, field, "size.maxWidth", params)
                }
                name.contains("minheight") || name.contains("min_height") -> {
                    extractDpFieldDirect(modNode, field, "size.minHeight", params)
                }
                name.contains("maxheight") || name.contains("max_height") -> {
                    extractDpFieldDirect(modNode, field, "size.maxHeight", params)
                }
                name == "width" -> extractDpFieldDirect(modNode, field, "size.width", params)
                name == "height" -> extractDpFieldDirect(modNode, field, "size.height", params)
            }
        }
    }

    private fun extractFillParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        var fraction = 1.0f
        var direction = ""

        for (field in fields) {
            try {
                field.isAccessible = true
                when {
                    field.name.contains("fraction") -> fraction = field.getFloat(modNode)
                    field.name.contains("direction") -> direction = field.get(modNode)?.toString() ?: ""
                }
            } catch (_: Exception) {}
        }

        val label = when {
            direction.contains("Horizontal") || direction.contains("Width") -> "fillMaxWidth"
            direction.contains("Vertical") || direction.contains("Height") -> "fillMaxHeight"
            else -> "fillMaxSize"
        }
        params.add(NodeParameter(label, "Float", if (fraction == 1.0f) "1.0" else "$fraction"))
    }

    private fun extractBackgroundParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        for (field in fields) {
            try {
                field.isAccessible = true
                when {
                    field.name == "color" -> {
                        val colorValue = field.getLong(modNode)
                        params.add(NodeParameter("background", "Color", formatColor(colorValue)))
                    }
                    field.name == "shape" -> {
                        val shape = field.get(modNode)
                        if (shape != null) {
                            params.add(NodeParameter("background.shape", "Shape", simplifyClassName(shape.javaClass.name)))
                        }
                    }
                }
            } catch (_: Exception) {
                // Color might not be a long field; try as object
                try {
                    field.isAccessible = true
                    if (field.name == "color") {
                        val obj = field.get(modNode)
                        if (obj != null) {
                            params.add(NodeParameter("background", "Color", obj.toString()))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun extractClipParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        for (field in fields) {
            if (field.name == "shape") {
                try {
                    field.isAccessible = true
                    val shape = field.get(modNode)
                    if (shape != null) {
                        params.add(NodeParameter("clip", "Shape", simplifyClassName(shape.javaClass.name)))
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun extractGraphicsLayerParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        for (field in fields) {
            try {
                field.isAccessible = true
                when (field.name) {
                    "alpha" -> {
                        val alpha = field.getFloat(modNode)
                        if (alpha != 1.0f) {
                            params.add(NodeParameter("alpha", "Float", "$alpha"))
                        }
                    }
                    "scaleX" -> {
                        val scale = field.getFloat(modNode)
                        if (scale != 1.0f) {
                            params.add(NodeParameter("scaleX", "Float", "$scale"))
                        }
                    }
                    "scaleY" -> {
                        val scale = field.getFloat(modNode)
                        if (scale != 1.0f) {
                            params.add(NodeParameter("scaleY", "Float", "$scale"))
                        }
                    }
                    "rotationZ" -> {
                        val rotation = field.getFloat(modNode)
                        if (rotation != 0.0f) {
                            params.add(NodeParameter("rotation", "Float", "${rotation}°"))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun extractBorderParams(modNode: Any, params: MutableList<NodeParameter>) {
        val fields = getAllDeclaredFields(modNode.javaClass)
        for (field in fields) {
            try {
                field.isAccessible = true
                when {
                    field.name == "width" -> {
                        val dp = formatDpValue(field.getFloat(modNode))
                        params.add(NodeParameter("border.width", "Dp", dp))
                    }
                    field.name == "color" -> {
                        try {
                            val colorValue = field.getLong(modNode)
                            params.add(NodeParameter("border.color", "Color", formatColor(colorValue)))
                        } catch (_: Exception) {
                            val obj = field.get(modNode)
                            if (obj != null) params.add(NodeParameter("border.color", "Color", obj.toString()))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ---- Modifier.Node chain navigation ----

    private fun getModifierNodeHead(layoutNode: Any): Any? {
        try {
            // LayoutNode.nodes is a NodeChain; NodeChain.head is the first Modifier.Node
            for (field in layoutNode.javaClass.declaredFields) {
                if (field.name == "nodes") {
                    field.isAccessible = true
                    val nodeChain = field.get(layoutNode) ?: continue
                    for (chainField in nodeChain.javaClass.declaredFields) {
                        if (chainField.name == "head") {
                            chainField.isAccessible = true
                            return chainField.get(nodeChain)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getNextModifierNode(current: Any): Any? {
        try {
            val childField = current.javaClass.getDeclaredField("child")
            childField.isAccessible = true
            return childField.get(current)
        } catch (_: Exception) {}
        // Try superclass
        try {
            var clazz: Class<*>? = current.javaClass.superclass
            while (clazz != null) {
                try {
                    val childField = clazz.getDeclaredField("child")
                    childField.isAccessible = true
                    return childField.get(current)
                } catch (_: Exception) {}
                clazz = clazz.superclass
            }
        } catch (_: Exception) {}
        return null
    }

    // ---- Utility methods ----

    private fun extractDpField(modNode: Any, fieldName: String, paramName: String, params: MutableList<NodeParameter>) {
        try {
            val field = modNode.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            val dp = formatDpValue(field.getFloat(modNode))
            params.add(NodeParameter(paramName, "Dp", dp))
        } catch (_: Exception) {}
    }

    private fun extractDpFieldDirect(modNode: Any, field: java.lang.reflect.Field, paramName: String, params: MutableList<NodeParameter>) {
        try {
            field.isAccessible = true
            val value = field.getFloat(modNode)
            if (value != Float.POSITIVE_INFINITY && value != Float.NEGATIVE_INFINITY) {
                val dp = formatDpValue(value)
                if (dp != "0.dp") {
                    params.add(NodeParameter(paramName, "Dp", dp))
                }
            }
        } catch (_: Exception) {}
    }

    private fun formatDpValue(value: Float): String {
        return if (value == value.toLong().toFloat()) {
            "${value.toLong()}.dp"
        } else {
            "${"%.1f".format(value)}.dp"
        }
    }

    private fun formatColor(colorLong: Long): String {
        // Compose Color is stored as a ULong; extract ARGB from the upper 32 bits
        val argb = (colorLong ushr 32).toInt()
        return "0x${Integer.toHexString(argb).uppercase().padStart(8, '0')}"
    }

    private fun getAllDeclaredFields(clazz: Class<*>): List<java.lang.reflect.Field> {
        val fields = mutableListOf<java.lang.reflect.Field>()
        var current: Class<*>? = clazz
        while (current != null && current.name.contains("compose")) {
            fields.addAll(current.declaredFields)
            current = current.superclass
        }
        return fields
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it, ignoreCase = true) }

    private fun simplifyModifierClassName(fullName: String): String {
        val simplified = fullName
            .substringAfterLast(".")
            .substringBefore("Kt\$")
            .substringBefore("\$\$")
            .substringBefore("$")
            .removeSuffix("Modifier")
            .removeSuffix("Impl")
            .removeSuffix("Element")
            .removeSuffix("Node")

        return when {
            simplified.isEmpty() -> ""
            simplified.all { it.isDigit() } -> ""
            simplified.length > 30 -> simplified.take(30)
            else -> simplified
        }
    }

    // ---- Node name extraction (unchanged core logic) ----

    private fun extractNodeName(node: Any): String {
        try {
            for (field in node.javaClass.declaredFields) {
                if (field.name == "measurePolicy") {
                    field.isAccessible = true
                    val policy = field.get(node) ?: continue
                    val policyName = policy.javaClass.name
                    val name = identifyFromClassName(policyName)
                    if (name.isNotEmpty() && name != "Node") return name
                }
            }
        } catch (_: Exception) {}

        return identifyFromClassName(node.javaClass.name)
    }

    private fun identifyFromClassName(fullName: String): String {
        return when {
            fullName.contains("BoxMeasurePolicy") || fullName.contains("BoxKt\$") -> "Box"
            fullName.contains("RowColumnMeasurePolicy") -> "Column/Row"
            fullName.contains("columnMeasurePolicy") || fullName.contains("ColumnKt\$") -> "Column"
            fullName.contains("rowMeasurePolicy") || fullName.contains("RowKt\$") -> "Row"
            fullName.contains("Surface") -> "Surface"
            fullName.contains("Scaffold") -> "Scaffold"
            fullName.contains("LazyList") || fullName.contains("LazyColumn") -> "LazyColumn"
            fullName.contains("LazyRow") -> "LazyRow"
            fullName.contains("LazyGrid") -> "LazyGrid"
            fullName.contains("EmptyMeasurePolicy") -> "Text"
            fullName.contains("BasicText") || fullName.contains("TextKt\$") || fullName.contains("TextStringSimpleElement") -> "Text"
            fullName.contains("Button") -> "Button"
            fullName.contains("Card") -> "Card"
            fullName.contains("Image") -> "Image"
            fullName.contains("Icon") -> "Icon"
            fullName.contains("Spacer") -> "Spacer"
            fullName.contains("CircleShape") || fullName.contains("RoundedCorner") -> "Shape"
            fullName.contains("AndroidView") -> "AndroidView"
            fullName.contains("Dialog") -> "Dialog"
            fullName.contains("Popup") -> "Popup"
            fullName.contains("TopAppBar") || fullName.contains("AppBar") -> "AppBar"
            fullName.contains("FloatingAction") || fullName.contains("Fab") -> "FAB"
            fullName.contains("TextField") || fullName.contains("OutlinedText") -> "TextField"
            fullName.contains("Checkbox") -> "Checkbox"
            fullName.contains("Switch") -> "Switch"
            fullName.contains("Radio") -> "RadioButton"
            fullName.contains("Slider") -> "Slider"
            fullName.contains("Tab") -> "Tab"
            fullName.contains("Divider") -> "Divider"
            fullName.contains("Snackbar") -> "Snackbar"
            fullName.contains("NavigationBar") || fullName.contains("BottomNav") -> "NavigationBar"
            fullName.contains("Drawer") -> "Drawer"
            fullName.contains("ModalBottomSheet") -> "BottomSheet"
            else -> simplifyClassName(fullName)
        }
    }

    private fun simplifyClassName(fullName: String): String {
        val simplified = fullName
            .substringAfterLast(".")
            .substringBefore("Kt\$")
            .substringBefore("\$\$")
            .substringBefore("$")
            .removeSuffix("MeasurePolicy")
            .removeSuffix("Impl")
            .removeSuffix("Element")

        return when {
            simplified.isEmpty() -> "Node"
            simplified.all { it.isDigit() } -> "Node"
            simplified.length > 30 -> simplified.take(30)
            else -> simplified
        }
    }

    // ---- Layout bounds extraction ----

    private fun extractBounds(node: Any): IntRect? {
        try {
            for (field in node.javaClass.declaredFields) {
                if (field.name == "outerCoordinator" || field.name == "innerCoordinator") {
                    field.isAccessible = true
                    val coordinator = field.get(node) ?: continue
                    for (coordField in coordinator.javaClass.declaredFields) {
                        if (coordField.name == "_measuredWidth" || coordField.name == "measuredWidth") {
                            coordField.isAccessible = true
                            val width = coordField.getInt(coordinator)
                            for (hField in coordinator.javaClass.declaredFields) {
                                if (hField.name == "_measuredHeight" || hField.name == "measuredHeight") {
                                    hField.isAccessible = true
                                    val height = hField.getInt(coordinator)
                                    if (width > 0 && height > 0) {
                                        return IntRect(0, 0, width, height)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ---- View hierarchy traversal ----

    private fun getRootLayoutNode(composeView: View): Any? {
        try {
            var clazz: Class<*>? = composeView.javaClass
            while (clazz != null) {
                for (field in clazz.declaredFields) {
                    if (field.name == "root" && field.type.name.contains("LayoutNode")) {
                        field.isAccessible = true
                        return field.get(composeView)
                    }
                }
                clazz = clazz.superclass
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get root LayoutNode", e)
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun getChildren(node: Any): List<Any> {
        try {
            for (field in node.javaClass.declaredFields) {
                if (field.name == "_children" || field.name == "children") {
                    field.isAccessible = true
                    val children = field.get(node)
                    if (children is List<*>) {
                        return children.filterNotNull()
                    }
                    if (children != null) {
                        try {
                            val sizeField = children.javaClass.getDeclaredField("size")
                            sizeField.isAccessible = true
                            val size = sizeField.getInt(children)
                            val contentField = children.javaClass.getDeclaredField("content")
                            contentField.isAccessible = true
                            val content = contentField.get(children) as? Array<*>
                            if (content != null) {
                                return content.take(size).filterNotNull()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            try {
                val method = node.javaClass.getMethod("getChildren\$ui_release")
                val result = method.invoke(node)
                if (result is List<*>) return result.filterNotNull()
            } catch (_: Exception) {}
            try {
                val method = node.javaClass.getMethod("getChildren")
                val result = method.invoke(node)
                if (result is List<*>) return result.filterNotNull()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get children", e)
        }
        return emptyList()
    }
}
