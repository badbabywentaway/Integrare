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
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Fires [onClick] once per physical left-click on a matching custom block,
 * regardless of whether the active provider fires PlayerInteractEvent,
 * BlockDamageEvent, or both for the same click.
 *
 * Unlike [CustomBlockHitListener], this never touches BlockBreakEvent and never
 * cancels anything - for features that only need to observe a click (e.g. an
 * activation sequence, a lever-like toggle) without consuming, removing, or
 * altering the block itself. If a feature needs to decide whether to consume the
 * block and cancel its break, use [CustomBlockHitListener] instead.
 *
 * @param matches Returns true for a [CustomBlockInfo] this listener should act on
 * @param onClick Called once per physical click
 */
class CustomBlockClickListener(
    private val provider: CustomItemProvider,
    private val matches: (CustomBlockInfo) -> Boolean,
    private val onClick: (Block, Player) -> Unit
) : Listener {

    // key -> tick added. Guards against a provider firing both Interact and
    // Damage for the same physical click; expires on its own rather than
    // requiring the caller to release it.
    private val recent = mutableMapOf<String, Int>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        tryHandle(block, event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDamage(event: BlockDamageEvent) {
        tryHandle(event.block, event.player)
    }

    private fun tryHandle(block: Block, player: Player) {
        val info = provider.getCustomBlock(block) ?: return
        if (!matches(info)) return

        val tick = Bukkit.getCurrentTick()
        val key = "${block.world.name}:${block.x}:${block.y}:${block.z}"
        recent.entries.removeIf { tick - it.value > GUARD_TICKS }
        if (key in recent) return
        recent[key] = tick

        onClick(block, player)
    }

    private companion object {
        /** How long a click guards its location against a second event for the same click. */
        const val GUARD_TICKS = 20
    }
}
