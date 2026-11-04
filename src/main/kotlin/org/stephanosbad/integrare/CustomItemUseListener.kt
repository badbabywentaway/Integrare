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

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

/**
 * Fires [onUse] when a player right-clicks while holding a matching custom
 * item - a wand/spell-casting trigger, not a block interaction.
 *
 * Unlike [CustomBlockHitListener], this wraps a single event with no dedup
 * guard: a held item doesn't go through a provider's own block-placement/
 * breaking pipeline the way a custom block does, so there's no confirmed
 * cross-provider ordering problem here yet to guard against. If real use
 * turns one up (e.g. a provider's own item-ability mechanic competing for the
 * same click), extend this then rather than guessing at the shape now.
 *
 * A thrown/splash item (trigger on impact, not on click) is a different
 * problem - tracking a projectile's launch and landing, not a held item - and
 * needs its own listener, not a variant of this one.
 *
 * [onUse] should return true to cancel the event (e.g. the item was consumed
 * casting a spell and shouldn't also open a container or place a block);
 * false lets the interaction proceed normally.
 *
 * @param matches Returns true for a [CustomItemInfo] this listener should act on
 * @param onUse Performs the action; return true to cancel the triggering event
 */
class CustomItemUseListener(
    private val provider: CustomItemProvider,
    private val matches: (CustomItemInfo) -> Boolean,
    private val onUse: (ItemStack, Player) -> Boolean
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val info = provider.getCustomItem(item) ?: return
        if (!matches(info)) return

        if (onUse(item, event.player)) event.isCancelled = true
    }
}
