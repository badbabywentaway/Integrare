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

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.CatalogRecipe
import org.stephanosbad.integrare.catalog.Ingredient
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.catalog.TextureSource
import java.io.File
import java.util.logging.Logger

class OraxenSetupTest {

    @TempDir
    lateinit var oraxenFolder: File

    private val catalog = ItemCatalog(
        "test",
        listOf(
            CatalogItem.Block("ruby", "Ruby", "block/ruby"),
            CatalogItem.Item("gem", "Gem", "item/gem", Material.PAPER)
        ),
        recipes = listOf(
            CatalogRecipe.Shapeless("gem", "gem", listOf(Ingredient.Vanilla("STONE"))),
            CatalogRecipe.Shaped("ruby", "ruby", listOf("GG"), mapOf('G' to Ingredient.Custom("gem")))
        ),
        textures = TextureSource { _, _ -> null }
    )

    private fun setup() = OraxenSetup(catalog, oraxenFolder, Logger.getLogger("OraxenSetupTest"), "Test") { Material.STONE }

    private fun count(file: String, key: String) =
        File(oraxenFolder, "recipes/$file").readLines().count { it == "$key:" }

    @Test
    fun `reinstalling after the items file is deleted does not duplicate recipes`() {
        File(oraxenFolder, "recipes").mkdirs()
        File(oraxenFolder, "recipes/shaped.yml").writeText("user_recipe:\n  shape:\n    - AA\n")

        setup().install()
        File(oraxenFolder, "items/test_blocks.yml").delete()
        setup().install()

        assertEquals(1, count("shapeless.yml", "test_gem"))
        assertEquals(1, count("shaped.yml", "test_ruby"))
        assertEquals(1, count("shaped.yml", "user_recipe"))
    }

    @Test
    fun `forced reinstall keeps other recipes and rewrites its own once`() {
        setup().install()
        File(oraxenFolder, "recipes/shapeless.yml").appendText("\nuser_recipe:\n  result:\n    minecraft_type: STONE\n")

        val result = setup().install(force = true)

        assertTrue(result.success)
        assertEquals(1, count("shapeless.yml", "test_gem"))
        assertEquals(1, count("shapeless.yml", "user_recipe"))
    }
}
