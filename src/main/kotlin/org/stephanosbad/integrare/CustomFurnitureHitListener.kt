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

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

/**
 * Fires [onHit] when a player attacks (left-clicks) a placed furniture entity -
 * natural "break to remove" for providers whose furniture is a Display entity paired
 * with an Interaction entity for its hitbox (e.g. ItemsAdderProvider's own placeFurniture),
 * since a bare Display can't be targeted at all and would otherwise have no hit-based
 * removal path the way Oraxen/Nexo's furniture already gets through their own native
 * placement/removal.
 *
 * [onHit] should return true if it consumed the hit (typically removing the furniture),
 * which cancels the event so the entity doesn't also take vanilla damage/knockback.
 *
 * @param matches Returns true for a [CustomFurnitureInfo] this listener should act on
 * @param onHit Performs the action; return true if it consumed the hit
 */
class CustomFurnitureHitListener(
    private val provider: CustomItemProvider,
    private val matches: (CustomFurnitureInfo) -> Boolean,
    private val onHit: (Entity, Player) -> Boolean
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val info = provider.getFurniture(event.entity) ?: return
        if (!matches(info)) return
        if (onHit(event.entity, player)) event.isCancelled = true
    }
}
