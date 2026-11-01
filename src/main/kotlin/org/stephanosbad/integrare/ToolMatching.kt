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

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.inventory.ItemStack

/**
 * Whether [item] is a vanilla tool whose material name contains [vanillaKeyword]
 * (e.g. a gold pickaxe when [vanillaKeyword] is "gold"), or a custom item from
 * this provider whose namespaced id contains [customKeyword], or - as a
 * fallback for items that don't resolve either way - whose display name
 * contains [vanillaKeyword] or [customKeyword].
 *
 * A common shape for plugins that gate an action behind "any gold or
 * custom-branded tool," regardless of which provider made the custom one.
 * `null` receiver (no provider available) only rules out the custom-item
 * check; the vanilla and display-name checks still run.
 */
fun CustomItemProvider?.matchesTool(item: ItemStack?, vanillaKeyword: String, customKeyword: String): Boolean {
    if (item == null || item.itemMeta == null) return false

    if (item.type.name.lowercase().contains(vanillaKeyword)) return true

    val customItem = this?.getCustomItem(item)
    if (customItem != null && customItem.namespacedId.lowercase().contains(customKeyword)) return true

    val displayName = item.itemMeta?.displayName() ?: return false
    val plain = PlainTextComponentSerializer.plainText().serialize(displayName).lowercase()
    return plain.contains(vanillaKeyword) || plain.contains(customKeyword)
}
