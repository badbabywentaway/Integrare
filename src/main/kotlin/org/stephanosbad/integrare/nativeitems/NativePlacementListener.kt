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
package org.stephanosbad.integrare.nativeitems

import org.bukkit.GameMode
import org.bukkit.Instrument
import org.bukkit.Material
import org.bukkit.Note
import org.bukkit.block.Block
import org.bukkit.block.data.type.NoteBlock as NoteBlockData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.NotePlayEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin
import org.stephanosbad.integrare.CustomItemInfo
import org.stephanosbad.integrare.provider.NativeItemProvider

/**
 * Handles placement and removal of native custom blocks.
 *
 * Native block items are PAPER (not directly placeable), so right-click is
 * intercepted to place a NOTE_BLOCK as the in-world carrier. The note block's
 * instrument+note state is set to the value registered in NativeItemProvider so
 * the resource pack can map each state to the correct per-item texture.
 *
 * Right-clicking an existing registered note block is cancelled to prevent
 * players changing the note (which would break the texture mapping).
 *
 * Requires paper-global.yml: block-updates.disable-noteblock-updates: true
 * Without it Minecraft resets the instrument on every block-physics update,
 * overriding the texture state.
 *
 * Only register this when the NativeItems provider is active.
 *
 * @param isPlaceable Returns false for registered items that are not blocks
 *   (tools, ingots, ...) so right-clicking with them is left alone.
 */
class NativePlacementListener(
    private val plugin: Plugin,
    private val provider: NativeItemProvider,
    private val isPlaceable: (CustomItemInfo) -> Boolean = { true }
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val clickedBlock = event.clickedBlock ?: return

        // Prevent activating a registered custom block on EITHER hand (note would play otherwise)
        if (clickedBlock.type == Material.NOTE_BLOCK && provider.getCustomBlock(clickedBlock) != null) {
            event.isCancelled = true
            return
        }

        if (event.hand != EquipmentSlot.HAND) return  // placement is main-hand only

        val item = event.item?.takeIf { !it.type.isAir } ?: return
        val itemInfo = provider.getCustomItem(item) ?: return

        if (!isPlaceable(itemInfo)) return

        val targetBlock = clickedBlock.getRelative(event.blockFace)
        if (!targetBlock.type.isAir) return

        event.isCancelled = true

        targetBlock.type = Material.NOTE_BLOCK

        val state = provider.getPlacementState(itemInfo.namespacedId)
        if (state != null) {
            applyNoteBlockState(targetBlock, state)
            // Minecraft resets the note block instrument via block-physics updates based on the
            // block below. Re-apply one tick later to survive that update.
            val loc = targetBlock.location
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                val block = loc.block
                if (block.type == Material.NOTE_BLOCK) {
                    applyNoteBlockState(block, state)
                }
            }, 1L)
        }

        provider.registerPlacedBlock(targetBlock.location, itemInfo.namespacedId)

        if (event.player.gameMode != GameMode.CREATIVE) {
            item.amount -= 1
        }
    }

    private fun applyNoteBlockState(block: Block, state: Pair<Instrument, Int>) {
        val noteData = block.blockData as? NoteBlockData ?: return
        noteData.instrument = state.first
        @Suppress("DEPRECATION")
        noteData.note = Note(state.second)
        noteData.isPowered = false
        block.blockData = noteData
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onNotePlay(event: NotePlayEvent) {
        if (provider.getCustomBlock(event.block) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val customBlock = provider.getCustomBlock(event.block) ?: return

        // Prevent the note_block item from dropping; give back the custom item instead
        event.isDropItems = false

        if (event.player.gameMode != GameMode.CREATIVE) {
            val customItem = provider.getItemStack(customBlock.namespacedId)
            if (customItem != null) {
                event.block.world.dropItemNaturally(event.block.location, customItem)
            }
        }

        provider.unregisterPlacedBlock(event.block.location)
    }
}
