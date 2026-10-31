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
import org.stephanosbad.integrare.catalog.ToolTier
import java.io.File
import java.util.logging.Logger

/**
 * Installs an [ItemCatalog] into Oraxen's plugin folder.
 *
 * Writes `items/<namespace>_blocks.yml`, appends recipes to Oraxen's shared
 * `recipes/shaped.yml` and `recipes/shapeless.yml` (keys prefixed with the
 * namespace), and adds models and textures to Oraxen's pack folder. Blocks are
 * note block mechanics numbered by `catalog.slotOrdered(ORAXEN)`.
 *
 * Oraxen item ids have no namespace, so catalog ids must be unique across the
 * server's Oraxen items.
 *
 * @param oraxenFolder Oraxen's plugin data folder
 * @param pluginName Name used in generated comments and the README
 * @param variationOffset Added to every block's `custom_variation` number, so
 *   this catalog's numbering doesn't collide with another Integrare-based
 *   plugin's blocks on the same server. Get this from [org.stephanosbad.integrare.SlotRegistry]
 *   rather than hardcoding it, unless migrating numbers already in use.
 */
class OraxenSetup(
    private val catalog: ItemCatalog,
    private val oraxenFolder: File,
    private val logger: Logger,
    private val pluginName: String,
    private val resolveMaterial: (String) -> Material? = Material::matchMaterial,
    private val variationOffset: Int = 0
) : ProviderSetup {

    override val kind = ProviderKind.ORAXEN

    private val namespace = catalog.namespace
    private val itemsFolder = File(oraxenFolder, "items")
    private val packFolder = File(oraxenFolder, "pack")
    private val recipesFolder = File(oraxenFolder, "recipes")
    private val itemsFile = File(itemsFolder, "${namespace}_blocks.yml")
    private val shapedFile = File(recipesFolder, "shaped.yml")
    private val shapelessFile = File(recipesFolder, "shapeless.yml")
    private val readmeFile = File(itemsFolder, "${pluginName.uppercase()}_README.txt")

    private val recipes = catalog.recipes.filter { it.isAvailable(resolveMaterial) }
    private val blocks = catalog.items.filterIsInstance<CatalogItem.Block>()

    override val nextSteps = listOf("Run /oraxen reload all", "Restart the server (recommended)")

    override val serverRequirements = ProviderSetup.NOTE_BLOCK_REQUIREMENTS

    private fun recipeKey(recipe: CatalogRecipe) = "${namespace}_${recipe.id}"

    override fun isAlreadySetup(): Boolean {
        if (!itemsFile.exists()) return false
        val shapeless = recipes.filterIsInstance<CatalogRecipe.Shapeless>().firstOrNull() ?: return true
        return shapelessFile.exists() && shapelessFile.readText().contains("${recipeKey(shapeless)}:")
    }

    override fun install(force: Boolean): SetupResult {
        val messages = mutableListOf<String>()

        if (isAlreadySetup()) {
            if (!force) {
                return SetupResult(
                    success = true,
                    alreadySetup = true,
                    messages = listOf(
                        "$pluginName Oraxen configuration already exists.",
                        "Files are located in: ${itemsFolder.absolutePath}"
                    )
                )
            }
            messages.add("Force mode enabled - deleting existing configuration...")
            try {
                removePreviousInstall()
                messages.add("  ✓ Old configuration deleted")
            } catch (e: Exception) {
                messages.add("  ✗ Failed to delete old configuration: ${e.message}")
                return SetupResult(false, false, messages)
            }
        }

        return try {
            messages.add("Creating directory structure...")
            itemsFolder.mkdirs()
            recipesFolder.mkdirs()

            messages.add("Generating items configuration...")
            itemsFile.writeText(itemsYaml())
            messages.add("  ✓ Items configuration created (${catalog.items.size} items)")

            messages.add("Generating recipes configuration...")
            appendRecipes()
            messages.add("  ✓ Recipes configuration created (${recipes.size} recipes)")

            messages.add("Generating model files...")
            messages.add("  ✓ Generated ${writeModels()} model files (blocks + items)")

            messages.add("Copying texture files...")
            val textures = catalog.items.count { item ->
                copyTexture(catalog, item, TextureResolution.REDUCED,
                    File(packFolder, "assets/$namespace/textures/${item.texture}.png"), logger)
            }
            messages.add("  ✓ Copied $textures texture files")

            messages.add("Creating README...")
            writeReadme(
                readmeFile, pluginName, "Oraxen", catalog,
                listOf(
                    "items/${itemsFile.name} - item definitions",
                    "recipes/shaped.yml, recipes/shapeless.yml - recipes prefixed '${namespace}_'",
                    "pack/assets/$namespace/textures/ - textures",
                    "pack/assets/minecraft/models/ - block and item models"
                ),
                nextSteps
            )
            messages.add("  ✓ ${readmeFile.name} created")

            messages.add("")
            messages.add("=== Setup Complete! ===")
            messages.add("Files copied to: ${itemsFolder.absolutePath}")
            messages.add("")
            messages.add("NEXT STEPS:")
            nextSteps.forEachIndexed { index, step -> messages.add("${index + 1}. $step") }
            messages.add("")

            SetupResult(success = true, alreadySetup = false, messages = messages)
        } catch (e: Exception) {
            messages.add("ERROR: ${e.message}")
            logger.severe("Failed to set up Oraxen configuration: ${e.message}")
            e.printStackTrace()
            SetupResult(success = false, alreadySetup = false, messages = messages)
        }
    }

    // ── Items ────────────────────────────────────────────────────────────────

    private fun itemsYaml(): String = buildString {
        appendLine("# $pluginName - Oraxen Items Configuration")
        appendLine("# Auto-generated by $pluginName plugin")
        appendLine("# Total: ${catalog.items.size} items (${blocks.size} blocks + ${catalog.items.size - blocks.size} other items)")
        appendLine("")

        val variations = catalog.slotOrdered(ProviderKind.ORAXEN)
            .filterIsInstance<CatalogItem.Block>()
            .withIndex().associate { (index, block) -> block.id to variationOffset + index + 1 }

        for (item in catalog.items) {
            when (item) {
                is CatalogItem.Block -> appendBlock(item, variations.getValue(item.id))
                is CatalogItem.Item -> appendItem(item)
            }
            appendLine("")
        }
    }

    private fun StringBuilder.appendBlock(block: CatalogItem.Block, variation: Int) {
        appendLine("${block.id}:")
        appendLine("  displayname: ${yamlQuote(block.displayNameFor(ProviderKind.ORAXEN))}")
        appendLine("  material: PAPER")
        appendLine("  Pack:")
        appendLine("    generate_model: false")
        appendLine("    model: item/${block.id}")
        appendLine("  Mechanics:")
        appendLine("    noteblock:")
        appendLine("      custom_variation: $variation")
        appendLine("      directional: false")
        appendLine("      hardness: ${block.hardness}")
        appendLine("      block_model: block/${block.id}")
        appendLine("      drop:")
        appendLine("        silktouch: false")
        appendLine("        minimal_type:")
        for (tier in ToolTier.entries.filter { it >= block.minimumTier }) {
            for (kind in block.breakTools) appendLine("          - ${tier.name}_${kind.name}")
        }
        appendLine("        loots:")
        appendLine("          - { oraxen_item: ${block.id}, probability: 1.0 }")
    }

    private fun StringBuilder.appendItem(item: CatalogItem.Item) {
        appendLine("${item.id}:")
        appendLine("  displayname: ${yamlQuote(item.displayNameFor(ProviderKind.ORAXEN))}")
        appendLine("  material: ${item.baseMaterialFor(ProviderKind.ORAXEN).name}")
        if (item.handheld) appendLine("  excludeFromInventory: false")
        if (item.lore.isNotEmpty()) {
            appendLine("  lore:")
            item.lore.forEach { appendLine("    - ${yamlQuote(it)}") }
        }
        appendLine("  Pack:")
        appendLine("    generate_model: true")
        appendLine("    parent_model: ${yamlQuote(if (item.handheld) "item/handheld" else "item/generated")}")
        appendLine("    textures:")
        appendLine("      layer0: $namespace:${item.texture}")
        item.durability?.let {
            appendLine("  Mechanics:")
            appendLine("    durability:")
            appendLine("      value: $it")
        }
        if (item.attackDamage != null && item.attackSpeed != null) {
            appendLine("  AttributeModifiers:")
            appendLine("    GENERIC_ATTACK_DAMAGE:")
            appendLine("      amount: ${formatWhole(item.attackDamage)}")
            appendLine("      slot: HAND")
            appendLine("    GENERIC_ATTACK_SPEED:")
            appendLine("      amount: ${item.attackSpeed}")
            appendLine("      slot: HAND")
        }
        if (item.miningTier != null && item.toolKind != null) {
            appendLine("  Components:")
            appendToolComponent(item, "    ")
        }
    }

    // ── Recipes ──────────────────────────────────────────────────────────────

    /**
     * Appends the catalog's recipes to Oraxen's shared recipe files, first removing
     * any entries a previous install left there (e.g. after its items file was
     * deleted), so repeated installs never duplicate keys.
     */
    private fun appendRecipes() {
        removeOwnRecipes()
        val shapeless = recipes.filterIsInstance<CatalogRecipe.Shapeless>()
        val shaped = recipes.filterIsInstance<CatalogRecipe.Shaped>()
        if (shapeless.isNotEmpty()) shapelessFile.appendText(buildString { shapeless.forEach { appendShapeless(it) } })
        if (shaped.isNotEmpty()) shapedFile.appendText(buildString { shaped.forEach { appendShaped(it) } })
    }

    private fun StringBuilder.appendIngredient(ingredient: Ingredient, indent: String) = when (ingredient) {
        is Ingredient.Vanilla -> appendLine("${indent}minecraft_type: ${ingredient.material}")
        is Ingredient.Custom -> appendLine("${indent}oraxen_item: ${ingredient.id}")
    }

    private fun StringBuilder.appendResult(recipe: CatalogRecipe) {
        appendLine("  result:")
        appendLine("    oraxen_item: ${recipe.result}")
        if (recipe.amount != 1) appendLine("    amount: ${recipe.amount}")
    }

    private fun StringBuilder.appendShapeless(recipe: CatalogRecipe.Shapeless) {
        appendLine("")
        appendLine("# $pluginName recipe")
        appendLine("${recipeKey(recipe)}:")
        appendResult(recipe)
        appendLine("  ingredients:")
        recipe.ingredients.forEachIndexed { index, ingredient ->
            appendLine("    ${'A' + index}:")
            appendLine("      amount: 1")
            appendIngredient(ingredient, "      ")
        }
    }

    private fun StringBuilder.appendShaped(recipe: CatalogRecipe.Shaped) {
        appendLine("")
        appendLine("# $pluginName recipe")
        appendLine("${recipeKey(recipe)}:")
        appendLine("  shape:")
        recipe.shape.forEach { appendLine("    - ${it.replace(' ', '_')}") }
        appendResult(recipe)
        appendLine("  ingredients:")
        for ((key, ingredient) in recipe.keys) {
            appendLine("    $key:")
            appendIngredient(ingredient, "      ")
        }
    }

    // ── Pack ─────────────────────────────────────────────────────────────────

    /**
     * Oraxen resolves `model: item/<id>` and `block_model: block/<id>` in the
     * minecraft namespace, so block and item models are written there.
     */
    private fun writeModels(): Int {
        val blockModels = File(packFolder, "assets/minecraft/models/block")
        val itemModels = File(packFolder, "assets/minecraft/models/item")
        blockModels.mkdirs()
        itemModels.mkdirs()
        for (block in blocks) {
            File(blockModels, "${block.id}.json").writeText("""
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "$namespace:${block.texture}"
  }
}
            """.trimIndent())
            File(itemModels, "${block.id}.json").writeText("""
{
  "parent": "minecraft:block/${block.id}"
}
            """.trimIndent())
        }
        return blocks.size * 2
    }

    /** Removes everything a previous [install] wrote, leaving other Oraxen content alone. */
    private fun removePreviousInstall() {
        itemsFile.delete()
        readmeFile.delete()
        File(packFolder, "assets/$namespace").deleteRecursively()
        for (block in blocks) {
            File(packFolder, "assets/minecraft/models/block/${block.id}.json").delete()
            File(packFolder, "assets/minecraft/models/item/${block.id}.json").delete()
        }
        removeOwnRecipes()
    }

    /** Removes every entry keyed with one of the catalog's recipe keys, and its comment. */
    private fun removeOwnRecipes() {
        val keys = catalog.recipes.map { Regex.escape(recipeKey(it)) }
        val recipeBlock = Regex(
            "(\\n# ${Regex.escape(pluginName)} recipe)?\\n(${keys.joinToString("|")}):.*?(?=\\n#|\\n\\w+:|$)",
            RegexOption.DOT_MATCHES_ALL
        )
        for (file in listOf(shapedFile, shapelessFile)) {
            if (file.exists()) file.writeText(("\n" + file.readText()).replace(recipeBlock, "").removePrefix("\n"))
        }
    }
}
