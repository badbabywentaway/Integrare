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

import org.bukkit.plugin.Plugin
import java.io.InputStream

/**
 * Texture sets a catalog can ship. Oraxen, Nexo and the native pack use [REDUCED]
 * to stay within resource pack size limits; ItemsAdder uses [FULL].
 */
enum class TextureResolution { FULL, REDUCED }

/** Opens the PNG for a [CatalogItem.texture] path, or returns null if it doesn't exist. */
fun interface TextureSource {
    fun open(texture: String, resolution: TextureResolution): InputStream?

    companion object {
        /**
         * Reads textures from [plugin]'s jar: `<root>/<texture>.png`, where root is
         * [fullRoot] or [reducedRoot] depending on the resolution requested.
         */
        fun fromPluginJar(plugin: Plugin, fullRoot: String, reducedRoot: String = fullRoot) =
            TextureSource { texture, resolution ->
                val root = if (resolution == TextureResolution.FULL) fullRoot else reducedRoot
                plugin.getResource("$root/$texture.png")
            }
    }
}
