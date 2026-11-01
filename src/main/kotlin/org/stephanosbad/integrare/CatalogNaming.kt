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

/**
 * Splits [CustomBlockInfo.blockName] into exactly 2 underscore-separated parts
 * (e.g. "cyan_a" -> `"cyan" to "a"`), for catalogs whose item ids follow a
 * `<prefix>_<suffix>` convention (not every catalog does). Already strips the
 * namespace, unlike re-splitting [CustomBlockInfo.namespacedId] on ":"
 * yourself. Null if [CustomBlockInfo.blockName] isn't shaped that way.
 */
fun CustomBlockInfo.splitName(): Pair<String, String>? {
    val parts = blockName.split("_")
    return if (parts.size == 2) parts[0] to parts[1] else null
}

/** The [CustomItemInfo] equivalent of [CustomBlockInfo.splitName]. */
fun CustomItemInfo.splitName(): Pair<String, String>? {
    val parts = itemName.split("_")
    return if (parts.size == 2) parts[0] to parts[1] else null
}
