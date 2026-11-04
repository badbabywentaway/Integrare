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

import org.bukkit.Material
import org.stephanosbad.integrare.ProviderKind

/**
 * One custom item of an [ItemCatalog].
 *
 * Display names and lore are MiniMessage strings.
 */
sealed interface CatalogItem {
    /** Item id without namespace, e.g. "ruby_block" */
    val id: String
    val displayName: String
    val lore: List<String>
    /** Texture path under `textures/` without extension, e.g. "block/ruby" */
    val texture: String
    /** Permission node for providers that gate items behind one (ItemsAdder) */
    val permission: String?
    /** Per-provider deviations, for keeping items that already exist on servers unchanged */
    val overrides: Map<ProviderKind, Overrides>

    fun displayNameFor(provider: ProviderKind): String = overrides[provider]?.displayName ?: displayName

    /**
     * A placeable full block with the same texture on every face.
     *
     * @property hardness Break time factor, as in vanilla block hardness
     * @property breakTools Tool kinds that drop the block when breaking it
     * @property minimumTier Lowest tool tier that drops the block
     */
    data class Block(
        override val id: String,
        override val displayName: String,
        override val texture: String,
        val hardness: Double = 0.8,
        val breakTools: List<ToolKind> = listOf(ToolKind.PICKAXE, ToolKind.AXE),
        val minimumTier: ToolTier = ToolTier.WOODEN,
        override val lore: List<String> = emptyList(),
        override val permission: String? = null,
        override val overrides: Map<ProviderKind, Overrides> = emptyMap()
    ) : CatalogItem

    /**
     * A non-placeable item, optionally a tool.
     *
     * @property baseMaterial Vanilla item the custom item is built on
     * @property handheld Rendered held like a tool rather than flat like an ingot
     * @property durability Maximum durability, replacing the base material's
     * @property miningTier Mines with the speed and harvest level of this tier's vanilla tool
     * @property attackDamage Main-hand attack damage modifier
     * @property attackSpeed Main-hand attack speed modifier
     */
    data class Item(
        override val id: String,
        override val displayName: String,
        override val texture: String,
        val baseMaterial: Material,
        val handheld: Boolean = false,
        val durability: Int? = null,
        val miningTier: ToolTier? = null,
        val attackDamage: Double? = null,
        val attackSpeed: Double? = null,
        override val lore: List<String> = emptyList(),
        override val permission: String? = null,
        override val overrides: Map<ProviderKind, Overrides> = emptyMap()
    ) : CatalogItem {
        fun baseMaterialFor(provider: ProviderKind): Material = overrides[provider]?.baseMaterial ?: baseMaterial

        /** The tool kind of [baseMaterial], e.g. PICKAXE for GOLDEN_PICKAXE */
        val toolKind: ToolKind? get() = ToolKind.entries.firstOrNull { baseMaterial.name.endsWith("_${it.name}") }
    }

    /** Provider-specific values that take precedence over the item's own. */
    data class Overrides(
        val displayName: String? = null,
        val baseMaterial: Material? = null
    )
}

enum class ToolKind {
    PICKAXE, AXE, SHOVEL, HOE;

    /** Block tag of blocks this kind of tool mines quickly */
    val mineableTag: String get() = "minecraft:mineable/${name.lowercase()}"
}

/**
 * Vanilla tool tiers, lowest first; names match the vanilla material prefixes.
 *
 * @property miningSpeed Speed on blocks the tool is meant for, as in vanilla
 * @property incorrectForDropsTag Block tag of blocks this tier is too low to harvest
 */
enum class ToolTier(val miningSpeed: Double, tagName: String) {
    WOODEN(2.0, "wooden"),
    STONE(4.0, "stone"),
    COPPER(5.0, "copper"),
    IRON(6.0, "iron"),
    GOLDEN(12.0, "gold"),
    DIAMOND(8.0, "diamond"),
    NETHERITE(9.0, "netherite");

    val incorrectForDropsTag: String = "minecraft:incorrect_for_${tagName}_tool"
}
