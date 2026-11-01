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

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.stephanosbad.integrare.ProviderKind
import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.catalog.TextureSource
import org.stephanosbad.integrare.provider.NativeItemProvider
import java.io.File
import java.util.logging.Logger

class NativeSetupTest {

    @TempDir
    lateinit var dataFolder: File

    private val png = TextureSource { _, _ -> byteArrayOf(1, 2, 3).inputStream() }

    private fun block(id: String) = CatalogItem.Block(id, id, "block/$id")
    private val tool = CatalogItem.Item("tool", "Tool", "item/tool", Material.GOLDEN_PICKAXE, handheld = true)

    private fun setup(catalog: ItemCatalog, cmdBase: Int = 1000) = NativeSetup(
        catalog, NativeItemProvider(), dataFolder, Logger.getLogger("NativeSetupTest"),
        cmdBase, "test-pack.zip", "Test Pack"
    )

    private fun read(path: String) = File(dataFolder, "native-pack/$path").readText()

    @Test
    fun `custom model data follows native slot order from cmdBase`() {
        val catalog = ItemCatalog(
            "t", listOf(block("a"), tool, block("b")), textures = png,
            slotOrder = mapOf(ProviderKind.NATIVE to listOf("b", "a", "tool"))
        )
        setup(catalog, cmdBase = 500).generatePack()

        val paper = read("assets/minecraft/items/paper.json")
        assertTrue(paper.indexOf("500.0, \"model\": {\"type\": \"minecraft:model\", \"model\": \"t:item/b\"") > 0)
        assertTrue(paper.contains("501.0, \"model\": {\"type\": \"minecraft:model\", \"model\": \"t:item/a\""))
        assertTrue(read("assets/minecraft/items/golden_pickaxe.json").contains("502.0"))
    }

    @Test
    fun `blocks get note block states in order, skipping items`() {
        val catalog = ItemCatalog("t", listOf(block("a"), tool, block("b")), textures = png)
        setup(catalog).generatePack()

        val states = read("assets/minecraft/blockstates/note_block.json")
        assertTrue(states.contains("\"instrument=harp,note=0,powered=false\": {\"model\": \"t:block/a\"}"))
        assertTrue(states.contains("\"instrument=harp,note=1,powered=false\": {\"model\": \"t:block/b\"}"))
        assertTrue(states.contains("\"instrument=harp,note=2,powered=false\": {\"model\": \"minecraft:block/note_block\"}"))
        assertTrue(states.contains("\"instrument=harp,note=0,powered=true\": {\"model\": \"minecraft:block/note_block\"}"))
    }

    @Test
    fun `the 26th block moves to the second instrument`() {
        val catalog = ItemCatalog("t", (0..25).map { block("b$it") }, textures = png)
        setup(catalog).generatePack()
        assertTrue(read("assets/minecraft/blockstates/note_block.json")
            .contains("\"instrument=basedrum,note=0,powered=false\": {\"model\": \"t:block/b25\"}"))
    }

    @Test
    fun `more blocks than note block states is rejected`() {
        val catalog = ItemCatalog("t", (0..NativeSetup.NOTE_BLOCK_STATES).map { block("b$it") }, textures = png)
        assertThrows<IllegalArgumentException> { setup(catalog) }
    }

    @Test
    fun `regenerating identical content gives an identical pack hash`() {
        val catalog = ItemCatalog("t", listOf(block("a"), tool), textures = png)
        val first = setup(catalog).generatePack()
        Thread.sleep(2100) // zip timestamps have 2s resolution
        val second = setup(catalog).generatePack(force = true)
        assertArrayEquals(first.packHash, second.packHash)
    }

    @Test
    fun `existing pack is kept unless forced`() {
        val catalog = ItemCatalog("t", listOf(block("a")), textures = png)
        setup(catalog).generatePack()
        val again = setup(catalog).generatePack()
        assertTrue(again.success)
        assertTrue(again.alreadySetup)
    }

    @Test
    fun `MiniMessage names equal the plain components native items were created with`() {
        // Existing native items were named with Component.text(...).color(...);
        // a structurally different name would stop new items stacking with them.
        assertEquals(
            Component.text("Cyan e").color(NamedTextColor.AQUA),
            MiniMessage.miniMessage().deserialize("<aqua>Cyan e")
        )
        assertEquals(
            Component.text("Pyrite Ingot").color(NamedTextColor.GOLD),
            MiniMessage.miniMessage().deserialize("<gold>Pyrite Ingot")
        )
    }
}
