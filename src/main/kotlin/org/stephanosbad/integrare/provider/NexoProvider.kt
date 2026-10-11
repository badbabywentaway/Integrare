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

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.stephanosbad.integrare.CustomBlockInfo
import org.stephanosbad.integrare.CustomItemInfo
import org.stephanosbad.integrare.CustomItemProvider

/**
 * Nexo implementation of the CustomItemProvider interface
 *
 * Provides integration with the Nexo plugin for custom items and blocks.
 *
 * **NOTE**: This implementation has not been tested with a live Nexo instance
 * as it requires a premium license. The implementation is based on Nexo's
 * public API documentation and follows the same patterns as Oraxen integration.
 *
 * **API Reference**: https://jd.nexomc.com/
 * **Documentation**: https://docs.nexomc.com/
 */
class NexoProvider : CustomItemProvider {

    override fun getItemStack(namespacedId: String): ItemStack? {
        // Nexo uses simple IDs without namespace (e.g., "ruby_block" instead of "myplugin:ruby_block")
        // We need to strip the namespace prefix if present
        val itemId = if (namespacedId.contains(":")) {
            namespacedId.substringAfter(":")
        } else {
            namespacedId
        }

        // NexoItems.itemFromId(itemID) returns an ItemBuilder
        // ItemBuilder.build() returns the ItemStack
        return NexoItems.itemFromId(itemId)?.build()
    }

    override fun getCustomItem(itemStack: ItemStack): CustomItemInfo? {
        // NexoItems.idFromItem(itemStack) returns the item ID or null
        val itemId = NexoItems.idFromItem(itemStack) ?: return null

        // Nexo doesn't use namespaces, so we'll use "nexo" as the namespace
        return CustomItemInfo(
            namespacedId = "nexo:$itemId",
            namespace = "nexo",
            itemName = itemId
        )
    }

    override fun getCustomBlock(block: Block): CustomBlockInfo? {
        // NexoBlocks API - need to check if there's a custom block at this location
        // Based on API documentation, Nexo tracks placed blocks
        // Note: This may need adjustment based on actual Nexo API behavior
        val mechanic = NexoBlocks.customBlockMechanic(block.location) ?: return null
        val blockId = mechanic.itemID

        // Nexo doesn't use namespaces, so we'll use "nexo" as the namespace
        return CustomBlockInfo(
            namespacedId = "nexo:$blockId",
            namespace = "nexo",
            blockName = blockId
        )
    }

    override fun removeCustomBlock(block: Block): Boolean {
        val nexoBlock = NexoBlocks.customBlockMechanic(block.location)
        return if (nexoBlock != null) {
            // Remove the Nexo custom block
            NexoBlocks.remove(block.location, null)
            true
        } else {
            // Fallback to vanilla removal
            block.type = Material.AIR
            false
        }
    }

    override fun getProviderName(): String {
        return "Nexo"
    }

    override fun isAvailable(): Boolean {
        return Bukkit.getPluginManager().getPlugin("Nexo") != null
    }
}
