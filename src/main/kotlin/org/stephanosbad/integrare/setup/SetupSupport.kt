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
package org.stephanosbad.integrare.setup

import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.catalog.TextureResolution
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Logger

// Helpers shared by the file-based provider setups

/** A double-quoted YAML scalar */
internal fun yamlQuote(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** 4.0 -> "4", 4.5 -> "4.5" */
internal fun formatWhole(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Copies [item]'s texture to [dest]; logs and returns false if the catalog has no such texture. */
internal fun copyTexture(
    catalog: ItemCatalog,
    item: CatalogItem,
    resolution: TextureResolution,
    dest: File,
    logger: Logger
): Boolean {
    val input = catalog.textures.open(item.texture, resolution)
    if (input == null) {
        logger.warning("Texture not found: ${item.texture}")
        return false
    }
    return try {
        dest.parentFile.mkdirs()
        input.use { src -> FileOutputStream(dest).use { src.copyTo(it) } }
        true
    } catch (e: Exception) {
        logger.warning("Failed to copy texture ${item.texture}: ${e.message}")
        false
    }
}

/**
 * Appends a vanilla `tool` data component giving [item] the mining speed and
 * harvest level of its tier's vanilla tool. Oraxen and Nexo share this format.
 */
internal fun StringBuilder.appendToolComponent(item: CatalogItem.Item, indent: String) {
    val tier = item.miningTier ?: return
    val kind = item.toolKind ?: return
    appendLine("${indent}tool:")
    appendLine("$indent  default_mining_speed: 1.0")
    appendLine("$indent  damage_per_block: 1")
    appendLine("$indent  rules:")
    appendLine("$indent    - tag: ${tier.incorrectForDropsTag}")
    appendLine("$indent      correct_for_drops: false")
    appendLine("$indent    - tag: ${kind.mineableTag}")
    appendLine("$indent      speed: ${tier.miningSpeed}")
    appendLine("$indent      correct_for_drops: true")
}

/**
 * A rough, blocky chair-like shape (seat, backrest, four legs) built entirely from the
 * one flat texture every furniture piece already has. This library doesn't yet support a
 * real custom 3D model per furniture item, so this is the placeholder render for any
 * furniture piece - strictly better than a flat icon standing up in the world (what it
 * replaced), even for a furniture piece that isn't conceptually a chair.
 *
 * [texture] must be a solid-filled image, not a transparent icon-style cutout (like the
 * ones this library's other icons use) - mapped onto a cuboid's faces, a mostly-transparent
 * icon renders as a near-invisible silhouette instead of a filled shape. Live-tested: this
 * broke the chair's appearance and looked like "a flat imprint"/"a red silhouette" until
 * the demo's furniture texture was redrawn as a solid fill.
 *
 * The `display.fixed` override is required and must be exactly this - without it, or with
 * identity, the model renders lying down or tipped in a DISPLAY_ENTITY/fixed-transform
 * furniture piece (confirmed live across several rotation values; Oraxen's own bundled
 * `pack/models/default/chair.json` needs a similar correction for its "fixed" entry, for
 * what's presumably the same underlying reason - some default non-identity baseline this
 * transform context applies that "ground"/dropped-item rendering doesn't).
 */
internal fun chairShapedModelJson(namespace: String, texture: String): String {
    val tex = "$namespace:$texture"
    fun cuboid(from: List<Int>, to: List<Int>) = """
        {
          "from": [${from.joinToString(", ")}],
          "to": [${to.joinToString(", ")}],
          "faces": {
            "down": { "texture": "#texture" },
            "up": { "texture": "#texture" },
            "north": { "texture": "#texture" },
            "south": { "texture": "#texture" },
            "west": { "texture": "#texture" },
            "east": { "texture": "#texture" }
          }
        }
    """.trimIndent()
    val elements = listOf(
        cuboid(listOf(2, 7, 2), listOf(14, 9, 14)), // seat
        cuboid(listOf(2, 9, 2), listOf(14, 16, 4)), // backrest
        cuboid(listOf(2, 0, 2), listOf(4, 7, 4)), // leg
        cuboid(listOf(10, 0, 2), listOf(12, 7, 4)), // leg
        cuboid(listOf(2, 0, 10), listOf(4, 7, 12)), // leg
        cuboid(listOf(10, 0, 10), listOf(12, 7, 12)) // leg
    ).joinToString(",\n")
    return """
        {
          "textures": {
            "particle": "$tex",
            "texture": "$tex"
          },
          "elements": [
        $elements
          ],
          "display": {
            "fixed": {
              "rotation": [-90, 0, 0],
              "translation": [0, 8, 0],
              "scale": [1, 1, 1]
            }
          }
        }
    """.trimIndent()
}

/** Writes a README describing what was generated and what to do next. */
internal fun writeReadme(
    file: File,
    pluginName: String,
    providerName: String,
    catalog: ItemCatalog,
    generated: List<String>,
    nextSteps: List<String>
) {
    val blocks = catalog.items.count { it is CatalogItem.Block }
    val items = catalog.items.size - blocks
    file.parentFile.mkdirs()
    file.writeText(buildString {
        appendLine("$pluginName - $providerName Integration")
        appendLine("=".repeat("$pluginName - $providerName Integration".length))
        appendLine()
        appendLine("AUTO-GENERATED by $pluginName. Changes are overwritten when setup is forced.")
        appendLine()
        appendLine("Contents:")
        generated.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Items: ${catalog.items.size} ($blocks blocks, $items other items), namespace '${catalog.namespace}'")
        appendLine("Recipes: ${catalog.recipes.size}")
        appendLine()
        appendLine("Next steps:")
        nextSteps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
    })
}
