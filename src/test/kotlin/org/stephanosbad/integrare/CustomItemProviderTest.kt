/*
 * Integrare - Custom item provider abstraction for Paper plugins
 * Copyright (C) 2025 StephanosBad
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <https://www.gnu.org/licenses/>.
 */
package org.stephanosbad.integrare

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for the CustomItemProvider data classes
 *
 * These tests focus on the contract and data structures rather than
 * implementation-specific behavior.
 */
class CustomItemProviderTest {

    // ==================== CustomItemInfo Tests ====================

    @Test
    fun `CustomItemInfo stores namespaced ID correctly`() {
        val info = CustomItemInfo(
            namespacedId = "myplugin:ruby_block",
            namespace = "myplugin",
            itemName = "ruby_block"
        )

        assertEquals("myplugin:ruby_block", info.namespacedId)
        assertEquals("myplugin", info.namespace)
        assertEquals("ruby_block", info.itemName)
    }

    @Test
    fun `CustomItemInfo equality works correctly`() {
        val info1 = CustomItemInfo("myplugin:ruby_block", "myplugin", "ruby_block")
        val info2 = CustomItemInfo("myplugin:ruby_block", "myplugin", "ruby_block")
        val info3 = CustomItemInfo("myplugin:jade_block", "myplugin", "jade_block")

        assertEquals(info1, info2, "Identical CustomItemInfo should be equal")
        assertNotEquals(info1, info3, "Different CustomItemInfo should not be equal")
    }

    // ==================== CustomBlockInfo Tests ====================

    @Test
    fun `CustomBlockInfo stores namespaced ID correctly`() {
        val info = CustomBlockInfo(
            namespacedId = "myplugin:ruby_block",
            namespace = "myplugin",
            blockName = "ruby_block"
        )

        assertEquals("myplugin:ruby_block", info.namespacedId)
        assertEquals("myplugin", info.namespace)
        assertEquals("ruby_block", info.blockName)
    }

    @Test
    fun `CustomBlockInfo equality works correctly`() {
        val info1 = CustomBlockInfo("myplugin:ruby_block", "myplugin", "ruby_block")
        val info2 = CustomBlockInfo("myplugin:ruby_block", "myplugin", "ruby_block")
        val info3 = CustomBlockInfo("myplugin:jade_block", "myplugin", "jade_block")

        assertEquals(info1, info2, "Identical CustomBlockInfo should be equal")
        assertNotEquals(info1, info3, "Different CustomBlockInfo should not be equal")
    }

    @ParameterizedTest
    @CsvSource(
        "myplugin:ruby_block, myplugin, ruby_block",
        "minecraft:stone, minecraft, stone",
        "oraxen:custom_block, oraxen, custom_block"
    )
    fun `CustomBlockInfo and CustomItemInfo can represent same object`(
        namespacedId: String,
        namespace: String,
        name: String
    ) {
        // Same object can be represented as both item and block
        val itemInfo = CustomItemInfo(namespacedId, namespace, name)
        val blockInfo = CustomBlockInfo(namespacedId, namespace, name)

        assertEquals(itemInfo.namespacedId, blockInfo.namespacedId,
            "Same object should have same namespacedID as item and block")
        assertEquals(itemInfo.namespace, blockInfo.namespace)
        assertEquals(itemInfo.itemName, blockInfo.blockName)
    }
}
