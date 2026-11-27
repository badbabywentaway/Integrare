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
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.stephanosbad.integrare.CustomBlockInfo
import org.stephanosbad.integrare.CustomFurnitureInfo
import org.stephanosbad.integrare.CustomItemInfo
import org.stephanosbad.integrare.CustomItemProvider
import java.util.UUID

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

    // Furniture is placed directly through Bukkit rather than ItemsAdder's own
    // CustomFurniture.spawnPreciseNonSolid, for two reasons found live-testing it:
    // it throws rather than returning null for an entity type it doesn't recognize
    // (e.g. a player), and - more fundamentally - the bare ItemDisplay it spawns has
    // no usable hitbox at all, since Display entities are hardcoded non-pickable in
    // vanilla. Pairing it with an invisible Interaction entity (vanilla's own answer
    // to that: Interaction is specifically a clickable, otherwise-invisible hitbox
    // companion for a Display entity) gives it a real one, same as the native
    // provider's own furniture would need if it had this problem. The appearance
    // still comes from ItemsAdder's own registered item via [CustomStack].
    private val furnitureKey = NamespacedKey("integrare", "itemsadder_furniture_id")
    private val furnitureLinkKey = NamespacedKey("integrare", "itemsadder_furniture_link")

    override fun placeFurniture(namespacedId: String, location: Location, blockFace: BlockFace): Entity? {
        val stack = CustomStack.getInstance(namespacedId)?.itemStack ?: return null
        val world = location.world ?: return null

        // ItemDisplayTransform.NONE renders the model centered on the entity's own location
        // rather than floor-anchored - a half-block Y lift puts its feet (y=0 in the model's
        // own coordinates) back on the ground instead of sunk halfway into the block below.
        // Live-tested: without this, the chair looked "halfway sunk into the block below it".
        val display = world.spawn(location.clone().add(0.0, 0.5, 0.0), ItemDisplay::class.java)
        display.setItemStack(stack)
        display.setRotation(yawFor(blockFace), 0f)
        // Explicit rather than relying on Bukkit's default: a custom furniture model (see
        // ItemsAdderSetup.appendFurniture) is authored upright with no transform baked in,
        // and NONE renders raw model geometry as-is - no per-context matrix to fight, unlike
        // Oraxen/Nexo's furniture, which uses "fixed" and needs one (see chairShapedModelJson).
        display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE

        val interaction = world.spawn(location, Interaction::class.java)
        interaction.interactionWidth = 1f
        interaction.interactionHeight = 1f

        for (entity in listOf(display, interaction)) {
            entity.persistentDataContainer.set(furnitureKey, PersistentDataType.STRING, namespacedId)
        }
        display.persistentDataContainer.set(furnitureLinkKey, PersistentDataType.STRING, interaction.uniqueId.toString())
        interaction.persistentDataContainer.set(furnitureLinkKey, PersistentDataType.STRING, display.uniqueId.toString())

        return display
    }

    override fun getFurniture(entity: Entity): CustomFurnitureInfo? {
        val id = entity.persistentDataContainer.get(furnitureKey, PersistentDataType.STRING) ?: return null
        val parts = id.split(":", limit = 2)
        return if (parts.size == 2) CustomFurnitureInfo(id, parts[0], parts[1]) else null
    }

    override fun removeFurniture(entity: Entity): Boolean {
        if (!entity.persistentDataContainer.has(furnitureKey, PersistentDataType.STRING)) return false
        entity.persistentDataContainer.get(furnitureLinkKey, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { Bukkit.getEntity(it) }
            ?.remove()
        entity.remove()
        return true
    }

    private fun yawFor(blockFace: BlockFace): Float = when (blockFace) {
        BlockFace.SOUTH -> 0f
        BlockFace.WEST -> 90f
        BlockFace.NORTH -> 180f
        BlockFace.EAST -> 270f
        else -> 0f
    }
}
