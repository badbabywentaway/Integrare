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

import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

/**
 * Fires [onPlace] when a player right-clicks a block while holding a matching custom
 * item, with the block-face-adjacent placement spot already resolved - the natural
 * right-click-to-place gesture a held block normally gets, which [CustomItemUseListener]
 * deliberately doesn't handle (it's scoped to wand/spell-style item use, not block
 * interactions - see its own doc).
 *
 * [location] is the center of the block adjacent to the clicked face, at that block's
 * floor level (e.g. clicking the top of a block places one block above it) - the usual
 * "place against the clicked face" target, not the player's own position.
 *
 * [onPlace] should return true to cancel the event (the item was placed and shouldn't
 * also trigger the clicked block's own right-click behavior, e.g. opening a container);
 * false lets the interaction proceed normally.
 *
 * @param matches Returns true for a [CustomItemInfo] this listener should act on
 * @param onPlace Performs the placement; return true to cancel the triggering event
 */
class CustomFurniturePlaceListener(
    private val provider: CustomItemProvider,
    private val matches: (CustomItemInfo) -> Boolean,
    private val onPlace: (ItemStack, Player, Location, BlockFace) -> Boolean
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val info = provider.getCustomItem(item) ?: return
        if (!matches(info)) return
        val clicked = event.clickedBlock ?: return
        val face = event.blockFace
        val target = clicked.getRelative(face)
        val location = Location(
            target.world, target.x + 0.5, target.y.toDouble(), target.z + 0.5, event.player.location.yaw, 0f
        )

        if (onPlace(item, event.player, location, face)) event.isCancelled = true
    }
}
