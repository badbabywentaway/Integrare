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
import dev.lone.itemsadder.api.CustomFurniture
import dev.lone.itemsadder.api.CustomStack
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.stephanosbad.integrare.CustomBlockInfo
import org.stephanosbad.integrare.CustomFurnitureInfo
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

    // Furniture is placed through ItemsAdder's own CustomFurniture.spawnPreciseNonSolid
    // now, not a manual Bukkit-spawned entity - that was a deliberate bypass for two bugs
    // found live-testing an earlier ItemsAdder build: it threw instead of returning null
    // for an item that doesn't resolve/isn't furniture-typed, and the bare ItemDisplay it
    // spawns appeared to have no usable hitbox (Display entities are hardcoded non-pickable
    // in vanilla, with no Interaction-entity pairing found decompiling this provider's own
    // furniture-spawning class). A later beta fixed the first outright (confirmed by
    // decompiling it: returns null cleanly now); the second turned out to already work live
    // despite the decompiled read - ItemsAdder's own furniture clearly does give it a
    // working hitbox and break interaction, by some mechanism this session's static
    // decompilation missed. Ground truth from live testing wins over that reading.
    private val furnitureKey = NamespacedKey("integrare", "itemsadder_furniture_id")

    // blockFace is unused here: spawnPreciseNonSolid takes no rotation parameter of its own,
    // reading the Location's own yaw for orientation instead - already set by every caller
    // in this codebase (see FurniturePlacement.kt/CustomFurniturePlaceListener), so nothing
    // is lost by not threading blockFace through to it.
    override fun placeFurniture(namespacedId: String, location: Location, blockFace: BlockFace): Entity? {
        // Live-tested: ItemsAdder's own spawn renders the model centered on the spawn
        // location rather than floor-anchored, same symptom (and same fix) this provider's
        // old manual ItemDisplayTransform.NONE spawn had - legs/seat sunk about half a block
        // into the ground. Shifted here, before spawning, not via a post-spawn teleport:
        // that moved the visible entity but left whatever hitbox ItemsAdder tracks
        // internally at the original, un-lifted spot, desyncing them - the chair looked
        // right but became very hard (or impossible) to hit accurately.
        val entity = CustomFurniture.spawnPreciseNonSolid(namespacedId, location.clone().add(0.0, 0.5, 0.0))?.entity
            ?: return null
        entity.persistentDataContainer.set(furnitureKey, PersistentDataType.STRING, namespacedId)
        return entity
    }

    override fun getFurniture(entity: Entity): CustomFurnitureInfo? {
        val id = entity.persistentDataContainer.get(furnitureKey, PersistentDataType.STRING) ?: return null
        val parts = id.split(":", limit = 2)
        return if (parts.size == 2) CustomFurnitureInfo(id, parts[0], parts[1]) else null
    }

    override fun removeFurniture(entity: Entity): Boolean {
        if (!entity.persistentDataContainer.has(furnitureKey, PersistentDataType.STRING)) return false
        CustomFurniture.remove(entity, false)
        return true
    }
}
