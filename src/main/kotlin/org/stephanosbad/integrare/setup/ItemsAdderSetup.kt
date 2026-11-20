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

import dev.lone.itemsadder.api.CustomStack
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapelessRecipe
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
    private val furnitureFile = File(configsFolder, "furniture.yml")
    private val itemsPacksFile = File(itemsAdderFolder, "data/items_packs.yml")

    private val blocks = catalog.items.filterIsInstance<CatalogItem.Block>()
    private val items = catalog.items.filterIsInstance<CatalogItem.Item>()
    private val furniture = catalog.items.filterIsInstance<CatalogItem.Furniture>()
    private val recipes = catalog.recipes.filter { it.isAvailable(resolveMaterial) }.filter { recipe ->
        // ItemsAdder's crafting_table ingredient matching only ever consumes 1 per
        // matched grid cell (confirmed by decompiling its recipe-loading and
        // crafting-match code - no "amount" is read for an input ingredient, and
        // listing the same material under two slot keys is rejected outright as
        // "Duplicate ingredient"), so a shapeless recipe needing more than one of
        // the same ingredient has no representable syntax on this provider.
        if (recipe is CatalogRecipe.Shapeless) {
            val duplicate = recipe.ingredients.groupingBy { ingredientText(it) }.eachCount().any { it.value > 1 }
            if (duplicate) {
                logger.warning(
                    "Recipe '${recipe.id}' needs more than one of the same ingredient in a single " +
                        "slot - ItemsAdder's crafting_table recipes can't express that. Skipped."
                )
                return@filter false
            }
        }
        true
    }

    private val miniMessage = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    override val nextSteps = listOf("Run /iazip to generate the resource pack", "Restart the server")

    override fun isAlreadySetup(): Boolean = blocksFile.exists() || itemsFile.exists() || furnitureFile.exists()

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
            if (furniture.isNotEmpty()) {
                messages.add("Generating furniture.yml...")
                furnitureFile.writeText(furnitureYaml())
                messages.add("  ✓ ${furniture.size} furniture pieces")
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
                    "configs/furniture.yml - furniture definitions",
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

    /**
     * Registers, directly through Bukkit's own recipe API, the one recipe shape
     * ItemsAdder's own crafting_table config can't express at all: a shapeless recipe
     * needing more than one of the same ingredient (rejected outright as "Duplicate
     * ingredient" - see the filter on [recipes]). A real Bukkit recipe works on any
     * ItemStack regardless of which plugin created it, so this reaches ItemsAdder's own
     * items through [CustomStack] rather than needing its own item/recipe system at all.
     *
     * Stonecutting is deliberately NOT ported here the same way, despite Bukkit's
     * StonecuttingRecipe API working fine in isolation (verified with an all-vanilla
     * recipe, and even with a vanilla item carrying an attribute-modifier component):
     * live-testing found that a stonecutting recipe whose result is one of ItemsAdder's
     * own items closes the stonecutter UI and returns the ingredients the instant the
     * result slot is clicked, but only when that item carries ItemsAdder's own
     * attribute_modifiers (a plain ItemsAdder item with no such component works fine).
     * No exception is logged client or server side, and ItemsAdder's own jar has no
     * stonecutter-related event handling at all (decompiled and confirmed), so whatever
     * ItemsAdder does differently for an attributed item's NBT/components isn't visible
     * from here. Rather than ship a result-slot that silently eats the player's
     * ingredients on an unknown subset of items, this provider just doesn't offer
     * stonecutting recipes at all (same as its own crafting_table config, see the
     * comment in [itemsYaml]).
     *
     * Call during onEnable, after this catalog has already been through at least one
     * setup+restart cycle - these items only resolve once ItemsAdder itself has loaded
     * them from the config [install] wrote, same requirement the rest of this class has.
     * Safe to call every boot: each recipe is re-registered under the same key, replacing
     * any previous registration rather than erroring or duplicating it.
     */
    fun registerExtraRecipes() {
        val duplicateShapeless = catalog.recipes.filterIsInstance<CatalogRecipe.Shapeless>()
            .filter { it.isAvailable(resolveMaterial) }
            .filter { recipe -> recipe.ingredients.groupingBy { ingredientText(it) }.eachCount().any { it.value > 1 } }
        for (recipe in duplicateShapeless) {
            if (skipFurnitureResult(recipe)) continue
            val result = resultStack(recipe) ?: continue
            val key = NamespacedKey(namespace, recipe.id)
            val bukkitRecipe = ShapelessRecipe(key, result)
            val counts = recipe.ingredients.groupingBy { ingredientText(it) }.eachCount()
            var resolved = true
            for ((text, count) in counts) {
                val ingredient = recipe.ingredients.first { ingredientText(it) == text }
                when (ingredient) {
                    is Ingredient.Vanilla -> {
                        val material = resolveMaterial(ingredient.material)
                        if (material == null) {
                            resolved = false
                            break
                        }
                        bukkitRecipe.addIngredient(count, material)
                    }
                    is Ingredient.Custom -> {
                        val itemStack = itemStackFor(ingredient.id)
                        if (itemStack == null) {
                            resolved = false
                            break
                        }
                        bukkitRecipe.addIngredient(count, itemStack)
                    }
                }
            }
            if (resolved) {
                Bukkit.removeRecipe(key)
                Bukkit.addRecipe(bukkitRecipe)
            }
        }
    }

    /**
     * ItemsAdder silently gives a furniture item a different base vanilla material than
     * whatever was configured (observed: PAPER configured, CHERRY_SAPLING actually given -
     * one of vanilla's "plant on a block" items, presumably so right-click placement has
     * something to hook into) - using one as a Bukkit recipe's result closes the crafting
     * UI the instant it's selected rather than producing it.
     */
    private fun skipFurnitureResult(recipe: CatalogRecipe): Boolean {
        if (catalog.item(recipe.result) !is CatalogItem.Furniture) return false
        logger.warning(
            "Recipe '${recipe.id}' produces furniture item '${recipe.result}' - ItemsAdder remaps " +
                "furniture to a different base material that breaks a Bukkit-registered recipe's " +
                "result slot. Skipped."
        )
        return true
    }

    private fun itemStackFor(id: String): ItemStack? = CustomStack.getInstance("$namespace:$id")?.itemStack?.clone()

    private fun resultStack(recipe: CatalogRecipe): ItemStack? =
        itemStackFor(recipe.result)?.also { it.amount = recipe.amount }

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

    private fun furnitureYaml(): String = buildString {
        appendHeader()
        appendLine("items:")
        for (piece in furniture) {
            appendFurniture(piece)
            appendLine()
        }
    }

    /**
     * Writes a static, non-seating, non-solid furniture piece as an item_display.
     * Schema reconstructed from ItemsAdder's own docs (wiki.itemsadder.com/adding-content/furnitures) -
     * key names/nesting corroborated across multiple fetches, but not copied from a verbatim
     * example the way Oraxen/Nexo's furniture schema was; not yet live-tested either way.
     */
    private fun StringBuilder.appendFurniture(item: CatalogItem.Furniture) {
        appendCommon(item)
        appendResource(Material.PAPER, item.texture)
        appendLine("    behaviours:")
        appendLine("      furniture:")
        appendLine("        entity: item_display")
        appendLine("        solid: false")
        appendLine("        placeable_on:")
        appendLine("          floor: true")
        appendLine("          ceiling: false")
        appendLine("          walls: false")
    }

    private fun itemsYaml(): String = buildString {
        appendHeader()
        val craftingTableRecipes = recipes.filter { it is CatalogRecipe.Shaped || it is CatalogRecipe.Shapeless }
        val cookingRecipes = recipes.filterIsInstance<CatalogRecipe.Furnace>()
        // Stonecutting recipes aren't generated here: core ItemsAdder has no stonecutter
        // recipe type of its own - it requires the separate ItemsAdderAdditions addon,
        // which most installs won't have. Any CatalogRecipe.Stonecutting is silently
        // skipped for this provider - see [registerExtraRecipes] for why it also isn't
        // ported through Bukkit directly the way the duplicate-ingredient case is.
        if (craftingTableRecipes.isNotEmpty() || cookingRecipes.isNotEmpty()) {
            appendLine("recipes:")
            if (craftingTableRecipes.isNotEmpty()) {
                appendLine("  crafting_table:")
                craftingTableRecipes.forEachIndexed { index, recipe ->
                    if (index > 0) appendLine()
                    appendCraftingTableRecipe(recipe)
                }
                appendLine()
            }
            if (cookingRecipes.isNotEmpty()) {
                appendLine("  cooking:")
                cookingRecipes.forEachIndexed { index, recipe ->
                    if (index > 0) appendLine()
                    appendCookingRecipe(recipe)
                }
                appendLine()
            }
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

    private fun StringBuilder.appendCraftingTableRecipe(recipe: CatalogRecipe) {
        appendLine("    ${recipe.id}_recipe:")
        appendLine("      enabled: true")
        when (recipe) {
            is CatalogRecipe.Shapeless -> {
                appendLine("      shapeless: true")
                appendLine("      ingredients:")
                // Recipes needing more than one of the same ingredient never reach here -
                // see the filter on `recipes` above.
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
            is CatalogRecipe.Furnace, is CatalogRecipe.Stonecutting ->
                error("appendCraftingTableRecipe called with non-crafting-table recipe ${recipe.id}")
        }
        appendLine("      result:")
        appendLine("        item: $namespace:${recipe.result}")
        appendLine("        amount: ${recipe.amount}")
    }

    /** Writes a "cooking" recipe (furnace/blast furnace/smoker) - see ItemsAdder's recipes/cooking docs. */
    private fun StringBuilder.appendCookingRecipe(recipe: CatalogRecipe.Furnace) {
        appendLine("    ${recipe.id}_recipe:")
        appendLine("      ingredient:")
        appendLine("        item: ${ingredientText(recipe.input)}")
        appendLine("      machines:")
        appendLine("        - FURNACE")
        appendLine("        - BLAST_FURNACE")
        appendLine("      exp: ${recipe.experience}")
        appendLine("      cook_time: ${recipe.cookingTime}")
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
