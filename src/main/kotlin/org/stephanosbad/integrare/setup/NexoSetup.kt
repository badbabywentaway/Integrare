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
import org.stephanosbad.integrare.ProviderKind
import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.CatalogRecipe
import org.stephanosbad.integrare.catalog.Ingredient
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.catalog.TextureResolution
import java.io.File
import java.util.logging.Logger

/**
 * Installs an [ItemCatalog] into Nexo's plugin folder.
 *
 * Writes `items/<namespace>_blocks.yml`, recipe files under `recipes/shaped/`
 * and `recipes/shapeless/`, and models and textures under
 * `pack/assets/<namespace>/`.
 *
 * Written against Nexo's documented config format and the keys its parsers
 * read; not yet run against a live Nexo server (premium license required).
 *
 * @param nexoFolder Nexo's plugin data folder
 * @param pluginName Name used in generated comments and the README
 */
class NexoSetup(
    private val catalog: ItemCatalog,
    private val nexoFolder: File,
    private val logger: Logger,
    private val pluginName: String,
    private val resolveMaterial: (String) -> Material? = Material::matchMaterial
) : ProviderSetup {

    override val kind = ProviderKind.NEXO

    private val namespace = catalog.namespace
    private val itemsFile = File(nexoFolder, "items/${namespace}_blocks.yml")
    private val assetsFolder = File(nexoFolder, "pack/assets/$namespace")
    private val shapedFile = File(nexoFolder, "recipes/shaped/$namespace.yml")
    private val shapelessFile = File(nexoFolder, "recipes/shapeless/$namespace.yml")
    private val readmeFile = File(nexoFolder, "${pluginName}_README.txt")

    private val recipes = catalog.recipes.filter { it.isAvailable(resolveMaterial) }
    private val blocks = catalog.items.filterIsInstance<CatalogItem.Block>()

    override val nextSteps = listOf(
        "Run /nexo reload all",
        "Restart the server (recommended)",
        "Players receive the updated resource pack on join"
    )

    override val serverRequirements = ProviderSetup.NOTE_BLOCK_REQUIREMENTS + listOf(
        "    disable-tripwire-updates: true",
        "    disable-chorus-plant-updates: true"
    )

    override fun isAlreadySetup(): Boolean =
        itemsFile.exists() && (recipes.isEmpty() || shapedFile.exists() || shapelessFile.exists())

    override fun install(force: Boolean): SetupResult {
        val messages = mutableListOf<String>()

        if (isAlreadySetup()) {
            if (!force) {
                return SetupResult(
                    success = true,
                    alreadySetup = true,
                    messages = listOf(
                        "$pluginName Nexo configuration already exists.",
                        "Files are located in: ${itemsFile.parentFile.absolutePath}"
                    )
                )
            }
            messages.add("Force mode enabled - deleting existing configuration...")
            listOf(itemsFile, shapedFile, shapelessFile, readmeFile).forEach { it.delete() }
            assetsFolder.deleteRecursively()
            messages.add("  ✓ Old configuration deleted")
        }

        return try {
            messages.add("Generating items configuration...")
            itemsFile.parentFile.mkdirs()
            itemsFile.writeText(itemsYaml())
            messages.add("  ✓ Items configuration created (${catalog.items.size} items)")

            messages.add("Copying texture files...")
            val textures = catalog.items.count { item ->
                copyTexture(catalog, item, TextureResolution.REDUCED,
                    File(assetsFolder, "textures/${item.texture}.png"), logger)
            }
            messages.add("  ✓ Copied $textures texture files")

            messages.add("Generating block models...")
            writeBlockModels()
            messages.add("  ✓ Generated ${blocks.size} block models")

            messages.add("Generating recipes...")
            writeRecipes()
            messages.add("  ✓ Recipes configuration created (${recipes.size} recipes)")

            writeReadme(
                readmeFile, pluginName, "Nexo", catalog,
                listOf(
                    "items/${itemsFile.name} - item definitions",
                    "recipes/shaped/$namespace.yml, recipes/shapeless/$namespace.yml - recipes",
                    "pack/assets/$namespace/ - textures and block models"
                ),
                nextSteps
            )
            messages.add("  ✓ ${readmeFile.name} created")

            messages.add("")
            messages.add("=== Setup Complete! ===")
            messages.add("")
            messages.add("NEXT STEPS:")
            nextSteps.forEachIndexed { index, step -> messages.add("${index + 1}. $step") }
            messages.add("")

            SetupResult(success = true, alreadySetup = false, messages = messages)
        } catch (e: Exception) {
            messages.add("ERROR: ${e.message}")
            logger.severe("Failed to set up Nexo integration: ${e.message}")
            e.printStackTrace()
            SetupResult(success = false, alreadySetup = false, messages = messages)
        }
    }

    /** Model path below models/block, e.g. "cyan/a" for texture "block/cyan/a" */
    private fun modelPath(block: CatalogItem.Block) = block.texture.substringAfter('/')

    private fun itemsYaml(): String = buildString {
        appendLine("# $pluginName Items Configuration for Nexo")
        appendLine("# Auto-generated by $pluginName plugin")
        appendLine("# DO NOT EDIT MANUALLY - changes are overwritten when setup is forced")
        appendLine()
        for (item in catalog.items) {
            when (item) {
                is CatalogItem.Block -> appendBlock(item)
                is CatalogItem.Item -> appendItem(item)
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendBlock(block: CatalogItem.Block) {
        appendLine("${block.id}:")
        appendLine("  itemname: ${yamlQuote(block.displayNameFor(ProviderKind.NEXO))}")
        appendLine("  material: NOTE_BLOCK")
        appendLine("  Pack:")
        appendLine("    model: $namespace:block/${modelPath(block)}")
        appendLine("  Mechanics:")
        appendLine("    custom_block:")
        appendLine("      type: NOTEBLOCK")
        appendLine("      drop:")
        appendLine("        silktouch: false")
        appendLine("        fortune: false")
        appendLine("        minimal_type: ${block.minimumTier.name}")
        appendLine("        best_tool: null")
        appendLine("        loots:")
        appendLine("          - nexo_item: ${block.id}")
    }

    private fun StringBuilder.appendItem(item: CatalogItem.Item) {
        appendLine("${item.id}:")
        appendLine("  itemname: ${yamlQuote(item.displayNameFor(ProviderKind.NEXO))}")
        appendLine("  material: ${item.baseMaterialFor(ProviderKind.NEXO).name}")
        if (item.lore.isNotEmpty()) {
            appendLine("  lore:")
            item.lore.forEach { appendLine("    - ${yamlQuote(it)}") }
        }
        appendLine("  Pack:")
        appendLine("    generate_model: true")
        appendLine("    parent_model: ${yamlQuote(if (item.handheld) "item/handheld" else "item/generated")}")
        appendLine("    textures:")
        appendLine("      layer0: $namespace:${item.texture}")
        val hasTool = item.miningTier != null && item.toolKind != null
        if (item.durability != null || hasTool) {
            appendLine("  Components:")
            item.durability?.let { appendLine("    max_damage: $it") }
            if (hasTool) appendToolComponent(item, "    ")
        }
        if (item.attackDamage != null && item.attackSpeed != null) {
            appendLine("  AttributeModifiers:")
            appendAttribute("attack_damage", formatWhole(item.attackDamage), item)
            appendAttribute("attack_speed", item.attackSpeed.toString(), item)
        }
    }

    private fun StringBuilder.appendAttribute(attribute: String, amount: String, item: CatalogItem.Item) {
        appendLine("    - attribute: $attribute")
        appendLine("      amount: $amount")
        appendLine("      operation: ADD_NUMBER")
        appendLine("      slot: mainhand")
        appendLine("      key: $namespace:${item.id}_$attribute")
    }

    private fun writeBlockModels() {
        for (block in blocks) {
            val file = File(assetsFolder, "models/block/${modelPath(block)}.json")
            file.parentFile.mkdirs()
            file.writeText("""
                {
                  "parent": "block/cube_all",
                  "textures": {
                    "all": "$namespace:${block.texture}"
                  }
                }
            """.trimIndent())
        }
    }

    private fun writeRecipes() {
        val shapeless = recipes.filterIsInstance<CatalogRecipe.Shapeless>()
        val shaped = recipes.filterIsInstance<CatalogRecipe.Shaped>()
        if (shapeless.isNotEmpty()) writeRecipeFile(shapelessFile, "Shapeless") { shapeless.forEach { appendShapeless(it) } }
        if (shaped.isNotEmpty()) writeRecipeFile(shapedFile, "Shaped") { shaped.forEach { appendShaped(it) } }
    }

    private fun writeRecipeFile(file: File, title: String, body: StringBuilder.() -> Unit) {
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("# $pluginName $title Recipes")
            appendLine("# Auto-generated by $pluginName plugin")
            appendLine()
            body()
        })
    }

    private fun StringBuilder.appendIngredient(ingredient: Ingredient) = when (ingredient) {
        is Ingredient.Vanilla -> appendLine("      minecraft_type: ${ingredient.material}")
        is Ingredient.Custom -> appendLine("      nexo_item: ${ingredient.id}")
    }

    private fun StringBuilder.appendResult(recipe: CatalogRecipe) {
        appendLine("${recipe.id}:")
        appendLine("  result:")
        appendLine("    nexo_item: ${recipe.result}")
        appendLine("    amount: ${recipe.amount}")
        appendLine("  ingredients:")
    }

    private fun StringBuilder.appendShapeless(recipe: CatalogRecipe.Shapeless) {
        appendResult(recipe)
        recipe.ingredients.forEachIndexed { index, ingredient ->
            appendLine("    ${'A' + index}:")
            appendLine("      amount: 1")
            appendIngredient(ingredient)
        }
        appendLine()
    }

    private fun StringBuilder.appendShaped(recipe: CatalogRecipe.Shaped) {
        appendResult(recipe)
        for ((key, ingredient) in recipe.keys) {
            appendLine("    $key:")
            appendLine("      amount: ${recipe.count(key)}")
            appendIngredient(ingredient)
        }
        appendLine("  shape:")
        recipe.shape.forEach { row -> appendLine("    - ${if (' ' in row) "'$row'" else row}") }
        appendLine()
    }
}
