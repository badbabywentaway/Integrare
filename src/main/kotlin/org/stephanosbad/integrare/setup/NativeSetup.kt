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

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Instrument
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.stephanosbad.integrare.ProviderKind
import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.CatalogRecipe
import org.stephanosbad.integrare.catalog.Ingredient
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.catalog.TextureResolution
import org.stephanosbad.integrare.provider.NativeItemProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installs an [ItemCatalog] into the built-in [NativeItemProvider]: registers its
 * items and recipes at startup, and generates the resource pack clients need.
 *
 * Slots follow `catalog.slotOrdered(NATIVE)`: the n-th item gets custom model data
 * `cmdBase + n`, and the n-th block gets note block state n (25 notes per
 * instrument, powered=false, instruments in vanilla order). Placed blocks are
 * stored as those states, so the slot order may only be appended to.
 *
 * Placed blocks need `block-updates.disable-noteblock-updates: true` in
 * paper-global.yml, otherwise the note block state and texture drift.
 *
 * @param zipFileName File name of the generated pack inside [dataFolder]
 * @param packDescription Description shown in the client's resource pack list
 * @param resolveMaterial Looks up vanilla materials by name; recipes using
 *   materials missing on this server version are skipped
 */
class NativeSetup(
    private val catalog: ItemCatalog,
    private val provider: NativeItemProvider,
    private val dataFolder: File,
    private val logger: Logger,
    private val cmdBase: Int,
    private val zipFileName: String,
    private val packDescription: String,
    private val resolveMaterial: (String) -> Material? = Material::matchMaterial
) : ProviderSetup {

    override val kind = ProviderKind.NATIVE

    override val nextSteps = listOf("Host the generated zip as the server resource pack")

    override val serverRequirements = ProviderSetup.NOTE_BLOCK_REQUIREMENTS

    private val namespace = catalog.namespace
    private val packRoot = File(dataFolder, "native-pack")
    private val packMeta = File(packRoot, "pack.mcmeta")
    private val miniMessage = MiniMessage.miniMessage()

    private val slotted: List<CatalogItem> = catalog.slotOrdered(ProviderKind.NATIVE)
    private val blocks: List<CatalogItem.Block> = slotted.filterIsInstance<CatalogItem.Block>()

    private val customModelData: Map<String, Int> =
        slotted.withIndex().associate { (index, item) -> item.id to cmdBase + index }

    init {
        require(blocks.size <= NOTE_BLOCK_STATES) {
            "Native provider supports at most $NOTE_BLOCK_STATES blocks, catalog has ${blocks.size}"
        }
    }

    val zipFile: File get() = File(dataFolder, zipFileName)

    /** Whether the resource pack has been generated */
    override fun isAlreadySetup(): Boolean = packMeta.exists()

    /** Generates the resource pack; see [generatePack] */
    override fun install(force: Boolean): SetupResult = generatePack(force)

    // ── Startup registration ─────────────────────────────────────────────────

    /** Registers every catalog item and recipe with the provider. Call during onEnable. */
    fun registerItems() {
        if (provider.registeredItemCount() > 0) return
        for (item in slotted) {
            val namespacedId = catalog.namespacedId(item)
            when (item) {
                is CatalogItem.Block -> {
                    provider.registerItem(namespacedId, customModelData.getValue(item.id), displayName(item))
                    val (instrument, note) = noteBlockState(blocks.indexOf(item))
                    provider.registerBlockState(namespacedId, Instrument.valueOf(instrument.bukkitName), note)
                }
                is CatalogItem.Item -> provider.registerItemStack(namespacedId, buildItemStack(item))
            }
        }
        registerRecipes()
        provider.loadPlacedBlocks(File(dataFolder, "placed-blocks.txt"))
        logger.info("NativeItemManager: registered ${provider.registeredItemCount()} items")
    }

    private fun displayName(item: CatalogItem) = miniMessage.deserialize(item.displayNameFor(ProviderKind.NATIVE))

    private fun buildItemStack(item: CatalogItem.Item): ItemStack {
        val stack = ItemStack(item.baseMaterialFor(ProviderKind.NATIVE))
        stack.editMeta { meta ->
            meta.displayName(displayName(item))
            if (item.lore.isNotEmpty()) meta.lore(item.lore.map { miniMessage.deserialize(it) })
        }
        stack.setData(
            DataComponentTypes.CUSTOM_MODEL_DATA,
            CustomModelData.customModelData().addFloat(customModelData.getValue(item.id).toFloat()).build()
        )
        item.durability?.let { stack.setData(DataComponentTypes.MAX_DAMAGE, it) }
        // Copy the mining rules of the tier's vanilla tool, e.g. IRON_PICKAXE, so speed
        // and harvest level match that tier rather than the base material's
        val tier = item.miningTier
        val kind = item.toolKind
        if (tier != null && kind != null) {
            resolveMaterial("${tier.name}_${kind.name}")?.let { vanilla ->
                ItemStack(vanilla).getData(DataComponentTypes.TOOL)?.let { stack.setData(DataComponentTypes.TOOL, it) }
            }
        }
        if (item.attackDamage != null && item.attackSpeed != null) {
            val attributes = ItemAttributeModifiers.itemAttributes()
                .addModifier(
                    Attribute.ATTACK_DAMAGE,
                    AttributeModifier(
                        NamespacedKey(namespace, "${item.id}_attack_damage"),
                        item.attackDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
                    )
                )
                .addModifier(
                    Attribute.ATTACK_SPEED,
                    AttributeModifier(
                        NamespacedKey(namespace, "${item.id}_attack_speed"),
                        item.attackSpeed, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
                    )
                )
                .build()
            stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes)
        }
        return stack
    }

    private fun registerRecipes() {
        for (recipe in catalog.recipes) {
            if (!recipe.isAvailable(resolveMaterial)) continue
            val result = provider.getItemStack("$namespace:${recipe.result}") ?: continue
            result.amount = recipe.amount
            val key = NamespacedKey(namespace, recipe.id)
            val bukkitRecipe = when (recipe) {
                is CatalogRecipe.Shapeless -> {
                    val choices = recipe.ingredients.map { choice(it) ?: continue }
                    ShapelessRecipe(key, result).apply { choices.forEach { addIngredient(it) } }
                }
                is CatalogRecipe.Shaped -> {
                    val choices = recipe.keys.mapValues { (_, ingredient) -> choice(ingredient) ?: continue }
                    ShapedRecipe(key, result).apply {
                        shape(*recipe.shape.toTypedArray())
                        choices.forEach { (char, choice) -> setIngredient(char, choice) }
                    }
                }
            }
            Bukkit.addRecipe(bukkitRecipe)
        }
    }

    private fun choice(ingredient: Ingredient): RecipeChoice? = when (ingredient) {
        is Ingredient.Vanilla -> resolveMaterial(ingredient.material)?.let { RecipeChoice.MaterialChoice(it) }
        is Ingredient.Custom -> provider.getItemStack("$namespace:${ingredient.id}")?.let { RecipeChoice.ExactChoice(it) }
    }

    // ── Resource pack ────────────────────────────────────────────────────────

    /** Generates and zips the resource pack under [dataFolder]. */
    fun generatePack(force: Boolean = false): SetupResult {
        val messages = mutableListOf<String>()

        if (isAlreadySetup() && !force) {
            return SetupResult(
                success = true,
                alreadySetup = true,
                messages = listOf("Native resource pack already exists at: ${packRoot.absolutePath}")
            )
        }

        if (force && packRoot.exists()) {
            messages.add("Force mode — deleting existing pack...")
            packRoot.deleteRecursively()
            messages.add("  Done")
        }

        return try {
            messages.add("Generating pack.mcmeta...")
            writeMcMeta()
            messages.add("  Done")

            messages.add("Generating block models...")
            messages.add("  Done (${writeBlockModels()} files)")

            messages.add("Generating item models...")
            messages.add("  Done (${writeItemModels()} files)")

            messages.add("Copying textures...")
            messages.add("  Done (${copyTextures()} files)")

            messages.add("Generating note_block state overrides...")
            messages.add("  Done (${writeNoteBlockStates()} custom states)")

            messages.add("Generating custom model data overrides...")
            messages.add("  Done (${writeLegacyOverrides()} files)")

            messages.add("Generating MC 1.21.4+ item definitions...")
            messages.add("  Done (${writeItemDefinitions()} files)")

            messages.add("Zipping pack...")
            val zip = zipPack()
            messages.add("  Done (${zip.name})")

            messages.add("Computing SHA-1 hash...")
            val hash = computePackHash(zip)
            messages.add("  Done")

            messages.add("")
            messages.add("=== Pack Ready! ===")
            messages.add("Zip: ${zip.absolutePath}")
            messages.add("NOTE: Set 'disable-noteblock-updates: true' in paper-global.yml")
            messages.add("  for stable block textures.")
            messages.add("")

            SetupResult(success = true, alreadySetup = false, messages = messages, zipFile = zip, packHash = hash)
        } catch (e: Exception) {
            messages.add("ERROR: ${e.message}")
            logger.severe("Failed to generate native resource pack: ${e.message}")
            e.printStackTrace()
            SetupResult(success = false, alreadySetup = false, messages = messages)
        }
    }

    private fun writeMcMeta() {
        write(packMeta, """
            {
              "pack": {
                "pack_format": 46,
                "description": "$packDescription"
              }
            }
        """.trimIndent())
    }

    /** Model path below models/item and models/block, e.g. "cyan/a" for texture "block/cyan/a" */
    private fun modelPath(item: CatalogItem) = item.texture.substringAfter('/')

    private fun writeBlockModels(): Int {
        for (block in blocks) {
            write(
                File(packRoot, "assets/$namespace/models/block/${modelPath(block)}.json"),
                """
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "$namespace:${block.texture}"
  }
}
                """.trimIndent()
            )
        }
        return blocks.size
    }

    private fun writeItemModels(): Int {
        for (item in slotted) {
            val file = File(packRoot, "assets/$namespace/models/item/${modelPath(item)}.json")
            when (item) {
                is CatalogItem.Block -> write(file, """{"parent": "$namespace:block/${modelPath(item)}"}""")
                is CatalogItem.Item -> write(
                    file,
                    """
{
  "parent": "${if (item.handheld) "minecraft:item/handheld" else "minecraft:item/generated"}",
  "textures": {
    "layer0": "$namespace:${item.texture}"
  }
}
                    """.trimIndent()
                )
            }
        }
        return slotted.size
    }

    private fun copyTextures(): Int {
        var count = 0
        for (item in slotted) {
            val input = catalog.textures.open(item.texture, TextureResolution.REDUCED)
            if (input == null) {
                logger.warning("Texture not found: ${item.texture}")
                continue
            }
            val dest = File(packRoot, "assets/$namespace/textures/${item.texture}.png")
            dest.parentFile.mkdirs()
            input.use { src -> FileOutputStream(dest).use { src.copyTo(it) } }
            count++
        }
        return count
    }

    /**
     * Writes assets/minecraft/blockstates/note_block.json listing every vanilla note
     * block state. States assigned to catalog blocks point at their models; all
     * others keep the vanilla note block model.
     */
    private fun writeNoteBlockStates(): Int {
        val assignments = blocks.withIndex().associate { (index, block) ->
            val (instrument, note) = noteBlockState(index)
            "${instrument.mcName},$note" to "$namespace:block/${modelPath(block)}"
        }

        val sb = StringBuilder()
        sb.append("{\n  \"variants\": {\n")
        var first = true
        for ((_, mcName) in INSTRUMENTS) {
            for (note in 0..24) {
                for (powered in listOf(false, true)) {
                    if (!first) sb.append(",\n")
                    first = false
                    val model = if (!powered) assignments["$mcName,$note"] ?: VANILLA_NOTE_BLOCK else VANILLA_NOTE_BLOCK
                    sb.append("""    "instrument=${mcName},note=${note},powered=${powered}": {"model": "${model}"}""")
                }
            }
        }
        sb.append("\n  }\n}")

        write(File(packRoot, "assets/minecraft/blockstates/note_block.json"), sb.toString())
        return assignments.size
    }

    /** Base material each item renders on: blocks are PAPER items until placed. */
    private fun baseMaterial(item: CatalogItem) = when (item) {
        is CatalogItem.Block -> Material.PAPER
        is CatalogItem.Item -> item.baseMaterialFor(ProviderKind.NATIVE)
    }

    private fun itemsByBaseMaterial(): Map<Material, List<CatalogItem>> =
        slotted.groupBy { baseMaterial(it) }
            .mapValues { (_, items) -> items.sortedBy { customModelData.getValue(it.id) } }

    /** Pre-1.21.4 clients: models/item/<material>.json with custom_model_data predicates. */
    private fun writeLegacyOverrides(): Int {
        val groups = itemsByBaseMaterial()
        for ((material, items) in groups) {
            val handheld = items.all { it is CatalogItem.Item && it.handheld }
            val overrides = items.joinToString(",\n    ") { item ->
                """{"predicate": {"custom_model_data": ${customModelData.getValue(item.id)}}, "model": "$namespace:item/${modelPath(item)}"}"""
            }
            write(
                File(packRoot, "assets/minecraft/models/item/${material.name.lowercase()}.json"),
                """
{
  "parent": "${if (handheld) "minecraft:item/handheld" else "minecraft:item/generated"}",
  "textures": {
    "layer0": "minecraft:item/${material.name.lowercase()}"
  },
  "overrides": [
    $overrides
  ]
}
                """.trimIndent()
            )
        }
        return groups.size
    }

    /** MC 1.21.4+ clients: items/<material>.json range_dispatch on custom_model_data. */
    private fun writeItemDefinitions(): Int {
        val groups = itemsByBaseMaterial()
        for ((material, items) in groups) {
            val entries = items.joinToString(",\n      ") { item ->
                """{"threshold": ${customModelData.getValue(item.id)}.0, "model": {"type": "minecraft:model", "model": "$namespace:item/${modelPath(item)}"}}"""
            }
            write(
                File(packRoot, "assets/minecraft/items/${material.name.lowercase()}.json"),
                """{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "index": 0,
    "entries": [
      $entries
    ],
    "fallback": {"type": "minecraft:model", "model": "minecraft:item/${material.name.lowercase()}"}
  }
}"""
            )
        }
        return groups.size
    }

    private fun write(file: File, text: String) {
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    /** Zips the pack with sorted entries and a fixed timestamp, so identical content hashes identically. */
    private fun zipPack(): File {
        val zip = zipFile
        if (zip.exists()) zip.delete()
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            packRoot.walkTopDown().filter { it.isFile }
                .sortedBy { it.relativeTo(packRoot).invariantSeparatorsPath }
                .forEach { file ->
                    val entry = ZipEntry(file.relativeTo(packRoot).invariantSeparatorsPath)
                    entry.timeLocal = ZIP_ENTRY_TIME
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
        }
        return zip
    }

    fun computePackHash(zip: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        zip.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun noteBlockState(blockIndex: Int): Pair<NoteInstrument, Int> =
        INSTRUMENTS[blockIndex / NOTES] to blockIndex % NOTES

    /**
     * An instrument by name; [Instrument] itself is only resolved when registering,
     * since loading that enum needs a running server.
     */
    private data class NoteInstrument(val bukkitName: String, val mcName: String)

    companion object {
        private const val NOTES = 25
        private const val VANILLA_NOTE_BLOCK = "minecraft:block/note_block"
        private val ZIP_ENTRY_TIME: LocalDateTime = LocalDateTime.of(1980, 1, 1, 0, 0)

        /** Note block instruments in vanilla order, with their blockstate names. */
        private val INSTRUMENTS = listOf(
            NoteInstrument("PIANO", "harp"),
            NoteInstrument("BASS_DRUM", "basedrum"),
            NoteInstrument("SNARE_DRUM", "snare"),
            NoteInstrument("STICKS", "hat"),
            NoteInstrument("BASS_GUITAR", "bass"),
            NoteInstrument("FLUTE", "flute"),
            NoteInstrument("BELL", "bell"),
            NoteInstrument("GUITAR", "guitar"),
            NoteInstrument("CHIME", "chime"),
            NoteInstrument("XYLOPHONE", "xylophone"),
            NoteInstrument("IRON_XYLOPHONE", "iron_xylophone"),
            NoteInstrument("COW_BELL", "cow_bell"),
            NoteInstrument("DIDGERIDOO", "didgeridoo"),
            NoteInstrument("BIT", "bit"),
            NoteInstrument("BANJO", "banjo"),
            NoteInstrument("PLING", "pling")
        )

        /** Unpowered note block states available for custom blocks */
        const val NOTE_BLOCK_STATES = 16 * NOTES
    }
}
