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

import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Fires [onHit] exactly once per physical hit on a matching custom block, regardless
 * of which Bukkit event the active provider fires first.
 *
 * Providers fire PlayerInteractEvent, BlockDamageEvent and BlockBreakEvent in
 * different orders for the same click - and the same provider can change order
 * between its own versions (observed first-hand: ItemsAdder's NativePackets line
 * fires BlockBreakEvent before Interact/Damage, while a later ItemsAdder beta,
 * Oraxen and Nexo all fire the normal Interact-then-Damage-then-Break order).
 * Rather than every consumer re-deriving that handling per feature, register one
 * listener per action and let it absorb the ordering difference.
 *
 * [onHit] should return true if it consumed the block (removed, transformed, or
 * otherwise handled it), which cancels the event that triggered it so the
 * provider doesn't also drop it as a vanilla item. Returning false lets the block
 * break normally - e.g. a miss that should still physically break and drop.
 *
 * @param matches Returns true for a [CustomBlockInfo] this listener should act on
 * @param onHit Performs the action; return true if it consumed the block
 */
class CustomBlockHitListener(
    private val provider: CustomItemProvider,
    private val matches: (CustomBlockInfo) -> Boolean,
    private val onHit: (Block, Player) -> Boolean
) : Listener {

    // key -> tick added. Guards against a provider firing more than one of
    // Interact/Damage/Break for the same physical hit; expires on its own so a
    // deferred onHit completing a tick or two later doesn't need to release it.
    private val processing = mutableMapOf<String, Int>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (tryHandle(block, event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDamage(event: BlockDamageEvent) {
        if (tryHandle(event.block, event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBreak(event: BlockBreakEvent) {
        if (tryHandle(event.block, event.player)) event.isCancelled = true
    }

    private fun tryHandle(block: Block, player: Player): Boolean {
        val info = provider.getCustomBlock(block) ?: return false
        if (!matches(info)) return false

        val tick = Bukkit.getCurrentTick()
        val key = "${block.world.name}:${block.x}:${block.y}:${block.z}"
        processing.entries.removeIf { tick - it.value > GUARD_TICKS }
        if (key in processing) return false
        processing[key] = tick

        return onHit(block, player)
    }

    private companion object {
        /** How long a hit guards its location against a second event for the same click. */
        const val GUARD_TICKS = 20
    }
}
