/*
 * Integrare demo plugin - exercises the library's own functionality
 * Copyright (C) 2025 StephanosBad
 */
package org.stephanosbad.integrare.demo

import org.bukkit.Material
import org.bukkit.plugin.Plugin
import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.CatalogRecipe
import org.stephanosbad.integrare.catalog.Ingredient
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.catalog.TextureSource

/**
 * Three simple catalog items, one of each kind Integrare supports, plus one
 * recipe of every kind - just enough to exercise every piece of the library
 * end to end against whichever provider is installed.
 */
object DemoCatalog {
    const val NAMESPACE = "integraredemo"

    fun create(plugin: Plugin): ItemCatalog {
        val block = CatalogItem.Block(
            id = "ruby_block",
            displayName = "<red>Ruby Block",
            texture = "block/ruby"
        )
        val sword = CatalogItem.Item(
            id = "ruby_sword",
            displayName = "<red>Ruby Sword",
            texture = "item/ruby_sword",
            baseMaterial = Material.DIAMOND_SWORD,
            handheld = true,
            attackDamage = 8.0,
            attackSpeed = 1.6
        )
        val chair = CatalogItem.Furniture(
            id = "ruby_chair",
            displayName = "<red>Ruby Chair",
            texture = "item/ruby_chair"
        )

        val recipes = listOf(
            // Shaped: 2 ruby blocks + a stick -> a ruby sword
            CatalogRecipe.Shaped(
                id = "ruby_sword_craft",
                result = "ruby_sword",
                shape = listOf("R", "R", "S"),
                keys = mapOf('R' to Ingredient.Custom("ruby_block"), 'S' to Ingredient.Vanilla("STICK"))
            ),
            // Shapeless: 4 redstone -> a ruby block
            CatalogRecipe.Shapeless(
                id = "ruby_block_combine",
                result = "ruby_block",
                ingredients = List(4) { Ingredient.Vanilla("REDSTONE") }
            ),
            // Furnace: smelt rotten flesh into a ruby block. Deliberately NOT an input
            // with its own vanilla furnace recipe (e.g. REDSTONE_ORE -> REDSTONE) -
            // that collides with the vanilla recipe for the same input, and vanilla
            // wins rather than being overridden.
            CatalogRecipe.Furnace(
                id = "ruby_block_smelt",
                result = "ruby_block",
                input = Ingredient.Vanilla("ROTTEN_FLESH"),
                experience = 1.0f,
                cookingTime = 200
            ),
            // Stonecutting: cut a ruby block into a ruby sword. ItemsAdder doesn't offer
            // this recipe at all - see ItemsAdderSetup.registerExtraRecipes.
            CatalogRecipe.Stonecutting(
                id = "ruby_sword_cut",
                result = "ruby_sword",
                input = Ingredient.Custom("ruby_block")
            )
        )

        return ItemCatalog(
            namespace = NAMESPACE,
            items = listOf(block, sword, chair),
            recipes = recipes,
            textures = TextureSource.fromPluginJar(plugin, fullRoot = "pack/assets/minecraft/textures")
        )
    }
}
