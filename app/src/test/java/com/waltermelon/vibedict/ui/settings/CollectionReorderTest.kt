package com.waltermelon.vibedict.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionReorderTest {

    @Test
    fun testReorderWithMissingIds() {
        // Initial state:
        // selectedIds contains "A", "B", "C", "D"
        // "B" is missing from loaded dictionaries (e.g. file deleted)
        // Visible list (selectedDicts) will be ["A", "C", "D"]
        
        val selectedIds = mutableListOf("A", "B", "C", "D")
        val visibleIds = listOf("A", "C", "D") // "B" is hidden

        // Move "C" (index 1 in visible list) to "A" (index 0 in visible list)
        // Visual move: ["A", "C", "D"] -> ["C", "A", "D"]
        // Expected selectedIds: ["C", "A", "B", "D"] OR ["C", "B", "A", "D"] depending on exact logic,
        // but crucially "C" must come before "A".
        // Let's assume we want to move the item to the position *before* the target item in the underlying list.
        
        // Simulation of the fix logic:
        val fromIndex = 1 // "C"
        val toIndex = 0   // "A"
        
        val fromId = visibleIds[fromIndex] // "C"
        val toId = visibleIds[toIndex]     // "A"
        
        val realFromIndex = selectedIds.indexOf(fromId) // 2
        val realToIndex = selectedIds.indexOf(toId)     // 0
        
        selectedIds.add(realToIndex, selectedIds.removeAt(realFromIndex))
        
        assertEquals(listOf("C", "A", "B", "D"), selectedIds)
    }

    @Test
    fun testReorderWithMissingIds_MoveDown() {
        // Initial state: ["A", "B", "C", "D"]
        // "B" is missing
        // Visible: ["A", "C", "D"]
        
        val selectedIds = mutableListOf("A", "B", "C", "D")
        val visibleIds = listOf("A", "C", "D")

        // Move "A" (index 0) to "C" (index 1)
        // Visual move: ["A", "C", "D"] -> ["C", "A", "D"]
        
        val fromIndex = 0 // "A"
        val toIndex = 1   // "C"
        
        val fromId = visibleIds[fromIndex] // "A"
        val toId = visibleIds[toIndex]     // "C"
        
        val realFromIndex = selectedIds.indexOf(fromId) // 0
        val realToIndex = selectedIds.indexOf(toId)     // 2
        
        selectedIds.add(realToIndex, selectedIds.removeAt(realFromIndex))
        
        assertEquals(listOf("B", "C", "A", "D"), selectedIds)
    }
}
