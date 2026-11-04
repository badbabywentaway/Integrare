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

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
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
 * Installs an [ItemCatalog] into ItemsAdder's plugin folder.
 *
 * Writes the namespace's content folder (`contents/<namespace>/configs/blocks.yml`
 * and `items.yml`, plus textures) and enables the namespace in
 * `data/items_packs.yml`. Admins then run /iazip to build the resource pack.
 *
 * @param itemsAdderFolder ItemsAdder's plugin data folder
 * @param pluginName Name used in the README
 */
class ItemsAdderSetup(
    private val catalog: ItemCatalog,
    private val itemsAdderFolder: File,
    private val logger: Logger,
    private val pluginName: String,
    private val resolveMaterial: (String) -> Material? = Material::matchMaterial
) : ProviderSetup {

    override val kind = ProviderKind.ITEMSADDER

    private val namespace = catalog.namespace
    private val contentFolder = File(itemsAdderFolder, "contents/$namespace")
    private val configsFolder = File(contentFolder, "configs")
    private val blocksFile = File(configsFolder, "blocks.yml")
    private val itemsFile = File(configsFolder, "items.yml")
    private val itemsPacksFile = File(itemsAdderFolder, "data/items_packs.yml")

    private val blocks = catalog.items.filterIsInstance<CatalogItem.Block>()
    private val items = catalog.items.filterIsInstance<CatalogItem.Item>()
    private val recipes = catalog.recipes.filter { it.isAvailable(resolveMaterial) }

    private val miniMessage = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    override val nextSteps = listOf("Run /iazip to generate the resource pack", "Restart the server")

    override fun isAlreadySetup(): Boolean = blocksFile.exists() || itemsFile.exists()

    override fun install(force: Boolean): SetupResult {
        val messages = mutableListOf<String>()

        if (isAlreadySetup()) {
            if (!force) {
                return SetupResult(
                    success = true,
                    alreadySetup = true,
                    messages = listOf(
                        "$pluginName ItemsAdder configuration already exists.",
                        "Files are located in: ${contentFolder.absolutePath}"
                    )
                )
            }
            messages.add("Force mode enabled - deleting existing configuration...")
            try {
                contentFolder.deleteRecursively()
                messages.add("  ✓ Old configuration deleted")
            } catch (e: Exception) {
                messages.add("  ✗ Failed to delete old configuration: ${e.message}")
                return SetupResult(false, false, messages)
            }
        }

        return try {
            messages.add("Creating directory structure...")
            configsFolder.mkdirs()

            messages.add("Enabling $namespace namespace...")
            if (enableNamespace()) {
                messages.add("  ✓ items_packs.yml updated")
            } else {
                messages.add("  ✗ Failed to update items_packs.yml")
                return SetupResult(false, false, messages)
            }

            if (blocks.isNotEmpty()) {
                messages.add("Generating blocks.yml...")
                blocksFile.writeText(blocksYaml())
                messages.add("  ✓ ${blocks.size} blocks")
            }
            if (items.isNotEmpty() || recipes.isNotEmpty()) {
                messages.add("Generating items.yml...")
                itemsFile.writeText(itemsYaml())
                messages.add("  ✓ ${items.size} items, ${recipes.size} recipes")
            }

            messages.add("Copying texture files...")
            val textures = catalog.items.count { item ->
                copyTexture(catalog, item, TextureResolution.FULL,
                    File(contentFolder, "resourcepack/assets/$namespace/textures/${item.texture}.png"), logger)
            }
            messages.add("  ✓ Copied $textures texture files")

            messages.add("Creating README...")
            writeReadme(
                File(contentFolder, "README.txt"), pluginName, "ItemsAdder", catalog,
                listOf(
                    "configs/blocks.yml - block definitions",
                    "configs/items.yml - item and recipe definitions",
                    "resourcepack/assets/$namespace/textures/ - textures"
                ),
                nextSteps
            )
            messages.add("  ✓ README.txt created")

            messages.add("")
            messages.add("=== Setup Complete! ===")
            messages.add("Files copied to: ${contentFolder.absolutePath}")
            messages.add("")
            messages.add("NEXT STEPS:")
            nextSteps.forEachIndexed { index, step -> messages.add("${index + 1}. $step") }
            messages.add("")

            SetupResult(success = true, alreadySetup = false, messages = messages)
        } catch (e: Exception) {
            messages.add("ERROR: ${e.message}")
            logger.severe("Failed to set up ItemsAdder configuration: ${e.message}")
            e.printStackTrace()
            SetupResult(success = false, alreadySetup = false, messages = messages)
        }
    }

    /** ItemsAdder uses legacy '&' colour codes */
    private fun legacyText(miniMessageText: String) = legacy.serialize(miniMessage.deserialize(miniMessageText))

    private fun StringBuilder.appendHeader() {
        appendLine("info:")
        appendLine("  namespace: $namespace")
        appendLine()
    }

    private fun StringBuilder.appendCommon(item: CatalogItem) {
        appendLine("  ${item.id}:")
        appendLine("    display_name: ${yamlQuote(legacyText(item.displayNameFor(ProviderKind.ITEMSADDER)))}")
        item.permission?.let { appendLine("    permission: $it") }
        if (item.lore.isNotEmpty()) {
            appendLine("    lore:")
            item.lore.forEach { appendLine("      - ${yamlQuote(legacyText(it))}") }
        }
    }

    private fun StringBuilder.appendResource(material: Material, texture: String) {
        appendLine("    resource:")
        appendLine("      material: ${material.name}")
        appendLine("      generate: true")
        appendLine("      textures:")
        appendLine("        - $texture")
    }

    private fun blocksYaml(): String = buildString {
        appendHeader()
        appendLine("items:")
        for (block in blocks) {
            appendCommon(block)
            appendResource(Material.PAPER, block.texture)
            appendLine("    specific_properties:")
            appendLine("      block:")
            appendLine("        placed_model:")
            appendLine("          type: REAL_NOTE")
            appendLine("          break_particles_material: NOTE_BLOCK")
            appendLine("        break_tools_whitelist:")
            breakTools(block).forEach { appendLine("          - $it") }
            appendLine("        hardness: ${block.hardness}")
            appendLine("        light_level: 0")
            appendLine()
        }
    }

    /** Vanilla tool suffixes, then the catalog's own tools of those kinds */
    private fun breakTools(block: CatalogItem.Block): List<String> =
        block.breakTools.map { "_${it.name}" }.sorted() +
            items.filter { it.toolKind in block.breakTools }.map { "$namespace:${it.id}" }.sorted()

    private fun itemsYaml(): String = buildString {
        appendHeader()
        if (recipes.isNotEmpty()) {
            appendLine("recipes:")
            appendLine("  crafting_table:")
            recipes.forEachIndexed { index, recipe ->
                if (index > 0) appendLine()
                appendRecipe(recipe)
            }
            appendLine()
        }
        if (items.isNotEmpty()) {
            appendLine("items:")
            items.forEachIndexed { index, item ->
                if (index > 0) appendLine()
                appendItem(item)
            }
        }
    }

    private fun ingredientText(ingredient: Ingredient) = when (ingredient) {
        is Ingredient.Vanilla -> ingredient.material
        is Ingredient.Custom -> "$namespace:${ingredient.id}"
    }

    private fun StringBuilder.appendRecipe(recipe: CatalogRecipe) {
        appendLine("    ${recipe.id}_recipe:")
        appendLine("      enabled: true")
        when (recipe) {
            is CatalogRecipe.Shapeless -> {
                appendLine("      shapeless: true")
                appendLine("      ingredients:")
                recipe.ingredients.forEachIndexed { index, ingredient ->
                    appendLine("        ${'A' + index}: ${ingredientText(ingredient)}")
                }
            }
            is CatalogRecipe.Shaped -> {
                appendLine("      pattern:")
                recipe.shape.forEach { row -> appendLine("        - ${if (' ' in row) yamlQuote(row) else row}") }
                appendLine("      ingredients:")
                recipe.keys.forEach { (key, ingredient) -> appendLine("        $key: ${ingredientText(ingredient)}") }
            }
        }
        appendLine("      result:")
        appendLine("        item: $namespace:${recipe.result}")
        appendLine("        amount: ${recipe.amount}")
    }

    private fun StringBuilder.appendItem(item: CatalogItem.Item) {
        appendCommon(item)
        appendResource(item.baseMaterialFor(ProviderKind.ITEMSADDER), item.texture)
        item.durability?.let {
            appendLine("    durability:")
            appendLine("      max_custom_durability: $it")
        }
        item.miningTier?.let {
            appendLine("    specific_properties:")
            appendLine("      tool:")
            appendLine("        tier: ${it.name}")
        }
        if (item.attackDamage != null && item.attackSpeed != null) {
            appendLine("    attribute_modifiers:")
            appendLine("      mainhand:")
            appendLine("        attackDamage: ${formatWhole(item.attackDamage)}")
            appendLine("        attackSpeed: ${item.attackSpeed}")
        }
    }

    /**
     * Enables the namespace in ItemsAdder's items_packs.yml, creating the file
     * if it is missing or has no `packs:` section.
     */
    private fun enableNamespace(): Boolean {
        return try {
            itemsPacksFile.parentFile.mkdirs()
            val newFile = "# ItemsAdder items packs configuration\n" +
                "# This file controls which custom item namespaces are loaded\n\n" +
                "packs:\n  $namespace:\n    enabled: true"
            val content = if (itemsPacksFile.exists()) {
                val existing = itemsPacksFile.readText()
                if (existing.contains("$namespace:")) return true
                val lines = existing.lines().toMutableList()
                val packsIndex = lines.indexOfFirst { it.trim().startsWith("packs:") }
                if (packsIndex == -1) {
                    newFile
                } else {
                    lines.add(packsIndex + 1, "  $namespace:")
                    lines.add(packsIndex + 2, "    enabled: true")
                    lines.joinToString("\n")
                }
            } else {
                newFile
            }
            itemsPacksFile.writeText(content)
            true
        } catch (e: Exception) {
            logger.severe("Failed to enable namespace in items_packs.yml: ${e.message}")
            false
        }
    }
}
