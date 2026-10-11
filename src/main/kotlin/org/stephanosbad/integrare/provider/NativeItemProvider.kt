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

import de.tr7zw.changeme.nbtapi.NBT
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Instrument
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.stephanosbad.integrare.CustomBlockInfo
import org.stephanosbad.integrare.CustomItemInfo
import org.stephanosbad.integrare.CustomItemProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Built-in fallback provider for servers without ItemsAdder, Oraxen, or Nexo.
 *
 * Items are identified by a custom NBT tag written via Item-NBT-API (MIT).
 * Placed blocks are tracked in-memory; NativePlacementListener maintains the registry.
 *
 * The consuming plugin registers its items (and their custom model data values)
 * during onEnable, before any item lookups are made.
 *
 * @param nbtKey NBT key used to tag items with their namespaced ID. Changing it
 *   orphans items already tagged with the previous key.
 */
class NativeItemProvider(private val nbtKey: String = DEFAULT_NBT_KEY) : CustomItemProvider {

    companion object {
        const val DEFAULT_NBT_KEY = "integrare_item_id"
    }

    private val itemRegistry = mutableMapOf<String, ItemStack>()
    private val placedBlocks = ConcurrentHashMap<Location, CustomBlockInfo>()
    private val blockStates = mutableMapOf<String, Pair<Instrument, Int>>()

    fun registerBlockState(namespacedId: String, instrument: Instrument, note: Int) {
        blockStates[namespacedId] = instrument to note
    }

    fun getPlacementState(namespacedId: String): Pair<Instrument, Int>? = blockStates[namespacedId]

    var packUrl: String? = null
    var packHash: ByteArray? = null
    val packUuid: UUID?
        get() = packHash?.let { UUID.nameUUIDFromBytes(it) }

    fun registerItem(namespacedId: String, customModelData: Int, displayName: Component) {
        val base = ItemStack(Material.PAPER).apply {
            editMeta { it.displayName(displayName) }
            setData(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloat(customModelData.toFloat()).build()
            )
        }
        // modify(ItemStack, Consumer) mutates in-place and returns void
        NBT.modify(base) { nbt: ReadWriteItemNBT -> nbt.setString(nbtKey, namespacedId) }
        itemRegistry[namespacedId] = base
    }

    fun registeredItemCount(): Int = itemRegistry.size

    fun registerItemStack(namespacedId: String, item: ItemStack) {
        NBT.modify(item) { nbt: ReadWriteItemNBT -> nbt.setString(nbtKey, namespacedId) }
        itemRegistry[namespacedId] = item
    }

    override fun getItemStack(namespacedId: String): ItemStack? =
        itemRegistry[namespacedId]?.clone()

    override fun getCustomItem(itemStack: ItemStack): CustomItemInfo? {
        // readNbt returns ReadableNBT directly, avoiding Function/Consumer overload ambiguity
        val id = NBT.readNbt(itemStack).getString(nbtKey)?.takeIf { it.isNotEmpty() } ?: return null
        val parts = id.split(":", limit = 2)
        if (parts.size != 2) return null
        return CustomItemInfo(id, parts[0], parts[1])
    }

    override fun getCustomBlock(block: Block): CustomBlockInfo? =
        placedBlocks[block.location]

    override fun removeCustomBlock(block: Block): Boolean {
        val existed = placedBlocks.remove(block.location) != null
        block.type = Material.AIR
        return existed
    }

    override fun getProviderName(): String = "NativeItems"

    override fun isAvailable(): Boolean = true

    fun registerPlacedBlock(location: Location, namespacedId: String) {
        val parts = namespacedId.split(":", limit = 2)
        if (parts.size == 2) {
            placedBlocks[location] = CustomBlockInfo(namespacedId, parts[0], parts[1])
            savePlacedBlocks()
        }
    }

    fun unregisterPlacedBlock(location: Location) {
        placedBlocks.remove(location)
        savePlacedBlocks()
    }

    // Persistence: survives server restarts so placed blocks are still recognised.

    private var persistenceFile: File? = null

    fun loadPlacedBlocks(file: File) {
        persistenceFile = file
        if (!file.exists()) return
        var loaded = 0
        file.forEachLine { line ->
            val parts = line.trim().split(" ")
            if (parts.size == 5) {
                val world = Bukkit.getWorld(parts[0]) ?: return@forEachLine
                val x = parts[1].toDoubleOrNull() ?: return@forEachLine
                val y = parts[2].toDoubleOrNull() ?: return@forEachLine
                val z = parts[3].toDoubleOrNull() ?: return@forEachLine
                val namespacedId = parts[4]
                val nsParts = namespacedId.split(":", limit = 2)
                if (nsParts.size == 2) {
                    placedBlocks[Location(world, x, y, z)] = CustomBlockInfo(namespacedId, nsParts[0], nsParts[1])
                    loaded++
                }
            }
        }
        if (loaded > 0) Bukkit.getLogger().info("NativeItems: restored $loaded placed blocks from disk")
    }

    private fun savePlacedBlocks() {
        val file = persistenceFile ?: return
        try {
            val sb = StringBuilder()
            for ((loc, info) in placedBlocks) {
                val world = loc.world ?: continue
                sb.appendLine("${world.name} ${loc.blockX} ${loc.blockY} ${loc.blockZ} ${info.namespacedId}")
            }
            file.writeText(sb.toString())
        } catch (e: Exception) {
            Bukkit.getLogger().warning("NativeItems: failed to save placed blocks: ${e.message}")
        }
    }
}
