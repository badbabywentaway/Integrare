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
package org.stephanosbad.integrare.provider

import dev.lone.itemsadder.api.CustomBlock
import dev.lone.itemsadder.api.CustomStack
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.stephanosbad.integrare.CustomBlockInfo
import org.stephanosbad.integrare.CustomItemInfo
import org.stephanosbad.integrare.CustomItemProvider

/**
 * ItemsAdder implementation of the CustomItemProvider interface
 *
 * Provides integration with the ItemsAdder plugin for custom items and blocks.
 */
class ItemsAdderProvider : CustomItemProvider {

    override fun getItemStack(namespacedId: String): ItemStack? {
        val customStack = CustomStack.getInstance(namespacedId)
        return customStack?.itemStack
    }

    override fun getCustomItem(itemStack: ItemStack): CustomItemInfo? {
        val customStack = CustomStack.byItemStack(itemStack) ?: return null
        val namespacedId = customStack.namespacedID
        val parts = namespacedId.split(":", limit = 2)

        return if (parts.size == 2) {
            CustomItemInfo(
                namespacedId = namespacedId,
                namespace = parts[0],
                itemName = parts[1]
            )
        } else {
            null
        }
    }

    override fun getCustomBlock(block: Block): CustomBlockInfo? {
        val customBlock = CustomBlock.byAlreadyPlaced(block) ?: return null
        val namespacedId = customBlock.namespacedID
        val parts = namespacedId.split(":", limit = 2)

        return if (parts.size == 2) {
            CustomBlockInfo(
                namespacedId = namespacedId,
                namespace = parts[0],
                blockName = parts[1]
            )
        } else {
            null
        }
    }

    override fun removeCustomBlock(block: Block): Boolean {
        val customBlock = CustomBlock.byAlreadyPlaced(block)
        return if (customBlock != null) {
            customBlock.remove()
            true
        } else {
            // Fallback to vanilla removal
            block.type = Material.AIR
            false
        }
    }

    override fun getProviderName(): String {
        return "ItemsAdder"
    }

    override fun isAvailable(): Boolean {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null
    }
}
