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

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.stephanosbad.integrare.provider.NativeItemProvider

/**
 * Sends the native resource pack to players when they join.
 *
 * Only register this when the NativeItems provider is running. If the pack has not
 * been generated yet (packUrl/packHash are null), joins are silently ignored until
 * the consuming plugin has generated the pack and started hosting it.
 *
 * @param packRequired Whether players must accept the pack; read on every join so
 *   config reloads take effect.
 */
class NativePackListener(
    private val provider: NativeItemProvider,
    private val packRequired: () -> Boolean = { false }
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val url = provider.packUrl ?: return
        val hash = provider.packHash ?: return
        val uuid = provider.packUuid ?: return

        event.player.setResourcePack(uuid, url, hash, null as net.kyori.adventure.text.Component?, packRequired())
    }
}
