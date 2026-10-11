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

import io.th0rgal.oraxen.api.OraxenBlocks
import io.th0rgal.oraxen.api.OraxenItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.stephanosbad.integrare.CustomBlockInfo
import org.stephanosbad.integrare.CustomItemInfo
import org.stephanosbad.integrare.CustomItemProvider

/**
 * Oraxen implementation of the CustomItemProvider interface
 *
 * Provides integration with the Oraxen plugin for custom items and blocks.
 */
class OraxenProvider : CustomItemProvider {

    override fun getItemStack(namespacedId: String): ItemStack? {
        // Oraxen uses simple IDs without namespace (e.g., "ruby_block" instead of "myplugin:ruby_block")
        // We need to strip the namespace prefix if present
        val itemId = if (namespacedId.contains(":")) {
            namespacedId.substringAfter(":")
        } else {
            namespacedId
        }

        return OraxenItems.getItemById(itemId)?.build()
    }

    override fun getCustomItem(itemStack: ItemStack): CustomItemInfo? {
        val itemId = OraxenItems.getIdByItem(itemStack) ?: return null

        // Oraxen doesn't use namespaces, so we'll use "oraxen" as the namespace
        return CustomItemInfo(
            namespacedId = "oraxen:$itemId",
            namespace = "oraxen",
            itemName = itemId
        )
    }

    override fun getCustomBlock(block: Block): CustomBlockInfo? {
        val oraxenBlock = OraxenBlocks.getOraxenBlock(block.location) ?: return null
        val blockId = oraxenBlock.itemID

        // Oraxen doesn't use namespaces, so we'll use "oraxen" as the namespace
        return CustomBlockInfo(
            namespacedId = "oraxen:$blockId",
            namespace = "oraxen",
            blockName = blockId
        )
    }

    override fun removeCustomBlock(block: Block): Boolean {
        val oraxenBlock = OraxenBlocks.getOraxenBlock(block.location)
        return if (oraxenBlock != null) {
            OraxenBlocks.remove(block.location, null)
            true
        } else {
            // Fallback to vanilla removal
            block.type = Material.AIR
            false
        }
    }

    override fun getProviderName(): String {
        return "Oraxen"
    }

    override fun isAvailable(): Boolean {
        return Bukkit.getPluginManager().getPlugin("Oraxen") != null
    }
}
