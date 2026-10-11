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
 * Information about a placed custom block
 *
 * @property namespacedId The full namespaced ID (e.g., "myplugin:ruby_block")
 * @property namespace The namespace part (e.g., "myplugin")
 * @property blockName The block name part (e.g., "ruby_block")
 */
data class CustomBlockInfo(
    val namespacedId: String,
    val namespace: String,
    val blockName: String
)
