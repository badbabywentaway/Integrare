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
package org.stephanosbad.integrare.catalog

import org.stephanosbad.integrare.ProviderKind

/**
 * Provider-neutral description of a plugin's custom items and recipes.
 *
 * The setup classes render a catalog into each provider's configuration files
 * (or, for the native provider, into registered items and a resource pack).
 *
 * Numeric slots — Oraxen custom_variation, native custom model data and native
 * note block states — are assigned in [items] order, unless [slotOrder] pins a
 * different order for a provider. Placed blocks and items in inventories are
 * stored as those numbers, so a slot order may only ever be appended to.
 *
 * @property namespace Namespace of every item, e.g. "myplugin" for "myplugin:ruby_block"
 * @property items Every custom item, in the order configuration files list them
 * @property recipes Crafting recipes producing catalog items
 * @property textures Where the PNG files named by [CatalogItem.texture] are read from
 * @property slotOrder Per-provider slot order; each list must name every item exactly once
 */
data class ItemCatalog(
    val namespace: String,
    val items: List<CatalogItem>,
    val recipes: List<CatalogRecipe> = emptyList(),
    val textures: TextureSource,
    val slotOrder: Map<ProviderKind, List<String>> = emptyMap()
) {
    private val byId: Map<String, CatalogItem> = items.associateBy { it.id }

    init {
        require(byId.size == items.size) {
            "Duplicate item ids: ${items.groupBy { it.id }.filterValues { it.size > 1 }.keys}"
        }
        for ((provider, order) in slotOrder) {
            require(order.size == order.toSet().size && order.toSet() == byId.keys) {
                "Slot order for $provider must name every item exactly once " +
                    "(missing ${byId.keys - order.toSet()}, unknown ${order.toSet() - byId.keys})"
            }
        }
        for (recipe in recipes) {
            val referenced = recipe.ingredients.filterIsInstance<Ingredient.Custom>().map { it.id } + recipe.result
            val unknown = referenced.filterNot { it in byId }
            require(unknown.isEmpty()) { "Recipe ${recipe.id} references unknown items $unknown" }
        }
    }

    fun item(id: String): CatalogItem? = byId[id]

    fun namespacedId(item: CatalogItem): String = "$namespace:${item.id}"

    /** Items in the order [provider] assigns numeric slots. */
    fun slotOrdered(provider: ProviderKind): List<CatalogItem> =
        slotOrder[provider]?.map { byId.getValue(it) } ?: items

    /** Whether [namespacedId] is a placeable block of this catalog. */
    fun isBlock(namespacedId: String): Boolean =
        namespacedId.substringBefore(':') == namespace &&
            byId[namespacedId.substringAfter(':')] is CatalogItem.Block
}
