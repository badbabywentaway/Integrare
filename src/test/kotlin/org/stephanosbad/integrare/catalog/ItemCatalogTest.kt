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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.stephanosbad.integrare.ProviderKind

class ItemCatalogTest {

    private val noTextures = TextureSource { _, _ -> null }
    private val ruby = CatalogItem.Block("ruby_block", "Ruby Block", "block/ruby")
    private val gem = CatalogItem.Item("gem", "Gem", "item/gem", Material.PAPER)

    @Test
    fun `slots follow item order by default`() {
        val catalog = ItemCatalog("test", listOf(ruby, gem), textures = noTextures)
        assertEquals(listOf(ruby, gem), catalog.slotOrdered(ProviderKind.NATIVE))
    }

    @Test
    fun `slot order can be pinned per provider`() {
        val catalog = ItemCatalog(
            "test", listOf(ruby, gem), textures = noTextures,
            slotOrder = mapOf(ProviderKind.NATIVE to listOf("gem", "ruby_block"))
        )
        assertEquals(listOf(gem, ruby), catalog.slotOrdered(ProviderKind.NATIVE))
        assertEquals(listOf(ruby, gem), catalog.slotOrdered(ProviderKind.ORAXEN))
    }

    @Test
    fun `slot order must name every item exactly once`() {
        assertThrows<IllegalArgumentException> {
            ItemCatalog("test", listOf(ruby, gem), textures = noTextures,
                slotOrder = mapOf(ProviderKind.NATIVE to listOf("gem")))
        }
        assertThrows<IllegalArgumentException> {
            ItemCatalog("test", listOf(ruby, gem), textures = noTextures,
                slotOrder = mapOf(ProviderKind.NATIVE to listOf("gem", "gem", "ruby_block")))
        }
    }

    @Test
    fun `duplicate ids are rejected`() {
        assertThrows<IllegalArgumentException> {
            ItemCatalog("test", listOf(ruby, ruby.copy(displayName = "Other")), textures = noTextures)
        }
    }

    @Test
    fun `recipes may only reference catalog items`() {
        assertThrows<IllegalArgumentException> {
            ItemCatalog("test", listOf(gem), textures = noTextures, recipes = listOf(
                CatalogRecipe.Shapeless("x", "missing", listOf(Ingredient.Vanilla("STONE")))
            ))
        }
    }

    @Test
    fun `shaped recipe keys must match the shape`() {
        assertThrows<IllegalArgumentException> {
            CatalogRecipe.Shaped("x", "gem", listOf("AB"), mapOf('A' to Ingredient.Vanilla("STONE")))
        }
    }

    @Test
    fun `shaped recipe counts key slots`() {
        val recipe = CatalogRecipe.Shaped(
            "pick", "gem", listOf("PPP", " S ", " S "),
            mapOf('P' to Ingredient.Custom("gem"), 'S' to Ingredient.Vanilla("STICK"))
        )
        assertEquals(3, recipe.count('P'))
        assertEquals(2, recipe.count('S'))
    }

    @Test
    fun `recipes with unknown vanilla materials are unavailable`() {
        val recipe = CatalogRecipe.Shapeless("x", "gem",
            listOf(Ingredient.Vanilla("IRON_INGOT"), Ingredient.Vanilla("FUTURE_MATERIAL")))
        assertFalse(recipe.isAvailable { if (it == "IRON_INGOT") Material.IRON_INGOT else null })
        assertTrue(recipe.isAvailable { Material.STONE })
    }

    @Test
    fun `isBlock checks namespace and kind`() {
        val catalog = ItemCatalog("test", listOf(ruby, gem), textures = noTextures)
        assertTrue(catalog.isBlock("test:ruby_block"))
        assertFalse(catalog.isBlock("test:gem"))
        assertFalse(catalog.isBlock("other:ruby_block"))
    }

    @Test
    fun `overrides replace display name and base material for one provider`() {
        val item = gem.copy(overrides = mapOf(
            ProviderKind.ORAXEN to CatalogItem.Overrides(displayName = "<gold>Gem", baseMaterial = Material.BRICK)
        ))
        assertEquals("<gold>Gem", item.displayNameFor(ProviderKind.ORAXEN))
        assertEquals("Gem", item.displayNameFor(ProviderKind.NEXO))
        assertEquals(Material.BRICK, item.baseMaterialFor(ProviderKind.ORAXEN))
        assertEquals(Material.PAPER, item.baseMaterialFor(ProviderKind.NATIVE))
    }

    @Test
    fun `tool kind comes from the base material`() {
        assertEquals(ToolKind.PICKAXE, gem.copy(baseMaterial = Material.GOLDEN_PICKAXE).toolKind)
        assertEquals(ToolKind.AXE, gem.copy(baseMaterial = Material.GOLDEN_AXE).toolKind)
        assertEquals(null, gem.toolKind)
    }
}
