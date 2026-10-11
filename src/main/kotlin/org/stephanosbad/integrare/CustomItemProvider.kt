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

import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

/**
 * Abstraction layer for custom item providers (ItemsAdder, Oraxen, Nexo, or Native)
 *
 * This interface allows a plugin to work with ItemsAdder, Oraxen, Nexo, or the
 * built-in Native provider by abstracting the common operations needed for custom items and blocks.
 */
interface CustomItemProvider {

    /**
     * Gets a custom ItemStack by its namespaced ID
     *
     * @param namespacedId The ID in format "namespace:item_name" (e.g., "myplugin:ruby_block")
     * @return The ItemStack, or null if the item doesn't exist
     */
    fun getItemStack(namespacedId: String): ItemStack?

    /**
     * Identifies a custom item from an ItemStack
     *
     * @param itemStack The ItemStack to check
     * @return CustomItemInfo if it's a custom item, null otherwise
     */
    fun getCustomItem(itemStack: ItemStack): CustomItemInfo?

    /**
     * Identifies a placed custom block
     *
     * @param block The block to check
     * @return CustomBlockInfo if it's a custom block, null otherwise
     */
    fun getCustomBlock(block: Block): CustomBlockInfo?

    /**
     * Removes a custom block from the world
     *
     * @param block The block to remove
     * @return true if successfully removed, false otherwise
     */
    fun removeCustomBlock(block: Block): Boolean

    /**
     * Gets the name of this provider implementation
     *
     * @return Provider name (e.g., "ItemsAdder", "Oraxen", "Nexo", "NativeItems")
     */
    fun getProviderName(): String

    /**
     * Checks if the provider plugin is available and loaded
     *
     * @return true if the provider is available
     */
    fun isAvailable(): Boolean
}
