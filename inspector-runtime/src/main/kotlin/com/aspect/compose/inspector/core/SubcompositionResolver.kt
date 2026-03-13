package com.aspect.compose.inspector.core

import android.util.Log
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree

private const val TAG = "SubcompositionResolver"

/**
 * Resolves subcompositions (e.g., LazyColumn, SubcomposeLayout, Dialog)
 * by inspecting CompositionData for child compositions.
 *
 * Subcompositions are stored in separate SlotTables, so they must be
 * discovered and traversed separately from the parent composition.
 */
@OptIn(UiToolingDataApi::class)
class SubcompositionResolver {

    /**
     * Find subcomposition data within a group's slots.
     * Returns a list of [Group] trees from discovered subcompositions.
     */
    fun findSubcompositions(group: Group): List<Group> {
        val subGroups = mutableListOf<Group>()
        findSubcompositionsRecursive(group, subGroups)
        return subGroups
    }

    private fun findSubcompositionsRecursive(group: Group, result: MutableList<Group>) {
        for (datum in group.data) {
            if (datum is CompositionData) {
                try {
                    result.add(datum.asTree())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse subcomposition", e)
                }
            }
        }
        for (child in group.children) {
            findSubcompositionsRecursive(child, result)
        }
    }
}
