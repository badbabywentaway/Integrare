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

import org.bukkit.inventory.ItemStack
import org.stephanosbad.integrare.catalog.ItemCatalog
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the [ItemStack] for each item of [catalog], for commands that hand
 * items to players (e.g. a `/give`-style command) rather than reaching into
 * [CustomItemProvider.getItemStack] themselves.
 *
 * A plain `by lazy` cache of this is the wrong tool here: it computes once and
 * never recomputes, so a provider that isn't ready yet at the first lookup
 * (setup hasn't run, or the server just booted) would be cached as a
 * permanent null, surviving even `/reload` - every later command silently
 * hands players nothing until a full restart. Call [invalidate] after
 * (re)running provider setup instead, and this recomputes on next use.
 *
 * @param catalog Resolves a bare item id (no namespace) to its [CatalogItem][org.stephanosbad.integrare.catalog.CatalogItem]
 * @param providerOf Looks up the active provider fresh on each miss, since it
 *   may not be ready yet the first time this is used
 */
class CatalogItems(
    private val catalog: ItemCatalog,
    private val providerOf: () -> CustomItemProvider?
) {
    private val cache = ConcurrentHashMap<String, ItemStack?>()

    /**
     * The [ItemStack] for the catalog item with this [id] (no namespace, e.g.
     * "ruby_block"), cloned so callers can safely mutate or hand out the
     * result. Null if [id] isn't in the catalog, or the provider has none for
     * it (not set up yet).
     */
    fun get(id: String): ItemStack? = cache.getOrPut(id) {
        val item = catalog.item(id) ?: return@getOrPut null
        providerOf()?.getItemStack(catalog.namespacedId(item))
    }?.clone()

    /** Forget every cached lookup, so the next [get] call resolves fresh. */
    fun invalidate() = cache.clear()
}
