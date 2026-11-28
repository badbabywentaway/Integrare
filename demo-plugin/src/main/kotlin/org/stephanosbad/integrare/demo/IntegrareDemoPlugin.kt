/*
 * Integrare demo plugin - exercises the library's own functionality
 * Copyright (C) 2025 StephanosBad
 */
package org.stephanosbad.integrare.demo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin
import org.stephanosbad.integrare.CatalogItems
import org.stephanosbad.integrare.CustomBlockClickListener
import org.stephanosbad.integrare.CustomBlockHitListener
import org.stephanosbad.integrare.CustomFurniturePlaceListener
import org.stephanosbad.integrare.CustomItemUseListener
import org.stephanosbad.integrare.CustomItemProviderManager
import org.stephanosbad.integrare.SlotRegistry
import org.stephanosbad.integrare.catalog.CatalogItem
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.command.CatalogGiveCommand
import org.stephanosbad.integrare.nativeitems.NativePackListener
import org.stephanosbad.integrare.nativeitems.NativePackServer
import org.stephanosbad.integrare.nativeitems.NativePlacementListener
import org.stephanosbad.integrare.provider.ItemsAdderProvider
import org.stephanosbad.integrare.provider.NativeItemProvider
import org.stephanosbad.integrare.setup.ItemsAdderSetup
import org.stephanosbad.integrare.setup.NativeSetup
import org.stephanosbad.integrare.setup.ProviderSetup
import java.io.File

/**
 * Exercises every piece of Integrare's own functionality with three simple
 * catalog items (a block, an item, a furniture piece) and one recipe of each
 * supported kind, against whichever custom item provider is installed.
 *
 * Not a real plugin - just a harness for testing the library itself. See
 * `/integraredemo` for what it can do.
 */
class IntegrareDemoPlugin : JavaPlugin() {

    lateinit var customItemProviderManager: CustomItemProviderManager
    lateinit var itemCatalog: ItemCatalog
    lateinit var catalogItems: CatalogItems
    private var nativePackServer: NativePackServer? = null

    /** Port the native provider's resource pack is hosted on, if used. */
    private val nativePackPort = 8090

    override fun onEnable() {
        itemCatalog = DemoCatalog.create(this)
        customItemProviderManager = CustomItemProviderManager(this)
        catalogItems = CatalogItems(itemCatalog) { customItemProviderManager.getProvider() }

        val result = customItemProviderManager.initialize()
        if (!result.success) {
            logger.severe("Failed to initialize a custom item provider:")
            result.messages.forEach { logger.severe(it) }
            server.pluginManager.disablePlugin(this)
            return
        }
        logger.info("Custom item provider: ${customItemProviderManager.getProviderName()}")

        val nativeProvider = customItemProviderManager.getProvider() as? NativeItemProvider
        if (nativeProvider != null) {
            // Register synchronously so lazy item lookups resolve correctly, same as
            // every other Integrare consumer (see CharmedChars.onEnable for precedent).
            nativeSetup(nativeProvider).registerItems()

            val setup = nativeSetup(nativeProvider)
            val existingZip = setup.zipFile
            if (existingZip.exists()) {
                nativeProvider.packHash = setup.computePackHash(existingZip)
                startNativePackHosting(nativeProvider, existingZip)
            }

            server.pluginManager.registerEvents(
                NativePlacementListener(this, nativeProvider) { itemCatalog.isBlock(it.namespacedId) }, this
            )
            server.pluginManager.registerEvents(NativePackListener(nativeProvider), this)
        }

        val itemsAdderProvider = customItemProviderManager.getProvider() as? ItemsAdderProvider
        if (itemsAdderProvider != null) {
            // The one recipe shape ItemsAdder's own crafting engine can't express at all
            // (a shapeless recipe needing more than one of the same ingredient) - registered
            // straight through Bukkit instead. See ItemsAdderSetup.registerExtraRecipes for
            // why stonecutting recipes aren't also ported this way.
            (providerSetup() as? ItemsAdderSetup)?.registerExtraRecipes()

            // Right-click-to-place for furniture, registered here (not left to ItemsAdder's
            // own native right-click handling for this item) so the resulting entity gets
            // this provider's own tracking tag - ItemsAdder's own placement wouldn't know to
            // set it, leaving it unfindable by getFurniture/removeFurniture afterward.
            // Registered at EventPriority.LOWEST and cancels the event on success, same
            // ordering trick CustomBlockHitListener documents elsewhere in this codebase, so
            // ItemsAdder's own handler for the same click never gets to run too.
            // CustomFurniturePlaceListener (not CustomItemUseListener - that one's scoped to
            // wand-style item use, not block interactions) resolves the clicked-face-adjacent
            // spot itself, same as a held block would place at, rather than wherever the
            // player happens to be standing.
            server.pluginManager.registerEvents(
                CustomFurniturePlaceListener(
                    provider = itemsAdderProvider,
                    matches = { it.itemName == "ruby_chair" },
                    onPlace = { _, player, location, _ ->
                        val item = itemCatalog.item("ruby_chair")
                        if (item != null) {
                            val entity = itemsAdderProvider.placeFurniture(
                                itemCatalog.namespacedId(item), location, yawToBlockFace(player.location.yaw)
                            )
                            // Placement doesn't consume the item on its own - this is the
                            // demo's own consumption, same as a real plugin's placement
                            // listener would need to do.
                            if (entity != null) {
                                val held = player.inventory.itemInMainHand
                                if (held.amount > 1) held.amount -= 1 else player.inventory.setItemInMainHand(null)
                            }
                        }
                        true
                    }
                ),
                this
            )

            // No break-to-remove listener here: ItemsAdder's own furniture spawning
            // (ItemsAdderProvider.placeFurniture, now using its native
            // CustomFurniture.spawnPreciseNonSolid) still doesn't pair a hitbox with the
            // Display entity it creates, confirmed by decompiling a newer beta - accepted
            // as a real limitation of ItemsAdder's own furniture system rather than a gap
            // to keep working around. This furniture can't be targeted or attacked at all,
            // so removal only ever goes through /integraredemo furniture remove (a sweep,
            // not a click - see DemoCommand.findFurnitureInSight).
        }

        val provider = customItemProviderManager.getProvider()
        if (provider != null) {
            // CustomBlockClickListener: observes a left-click/damage on the ruby block
            // without consuming it - logs only, the block still breaks normally after.
            server.pluginManager.registerEvents(
                CustomBlockClickListener(
                    provider = provider,
                    matches = { it.blockName == "ruby_block" },
                    onClick = { _, player -> player.sendMessage(Component.text("(click detected on the ruby block)").color(NamedTextColor.GRAY)) }
                ),
                this
            )

            // CustomBlockHitListener: fires exactly once per hit regardless of provider
            // event ordering; here it just confirms the break and lets it proceed (false).
            server.pluginManager.registerEvents(
                CustomBlockHitListener(
                    provider = provider,
                    matches = { it.blockName == "ruby_block" },
                    onHit = { _, player ->
                        player.sendMessage(Component.text("You hit the Ruby Block!").color(NamedTextColor.RED))
                        false
                    }
                ),
                this
            )

            // CustomItemUseListener: right-click while holding the ruby sword
            server.pluginManager.registerEvents(
                CustomItemUseListener(
                    provider = provider,
                    matches = { it.itemName == "ruby_sword" },
                    onUse = { _, player ->
                        player.sendMessage(Component.text("You swing the Ruby Sword!").color(NamedTextColor.RED))
                        false
                    }
                ),
                this
            )
        }

        val giveCommand = CatalogGiveCommand(itemCatalog, catalogItems, "integraredemo.admin")
        getCommand("integraredemo")?.setExecutor(DemoCommand(this, giveCommand))

        logger.info("IntegrareDemo enabled. Run /integraredemo setup to install the demo catalog.")
    }

    override fun onDisable() {
        nativePackServer?.stop()
        nativePackServer = null
    }

    /**
     * Native provider setup for [itemCatalog]. Reserves its numeric slots through
     * [SlotRegistry] rather than hardcoding them, so this demo plugin doesn't collide
     * with CharmedChars or any other Integrare-based plugin also on the native
     * fallback on the same server - same pattern CharmedChars itself uses.
     */
    fun nativeSetup(provider: NativeItemProvider): NativeSetup {
        val slots = SlotRegistry(this)
        val cmdRange = slots.reserve("native.cmd", DemoCatalog.NAMESPACE, itemCatalog.items.size, preferredStart = 0)
        val blockCount = itemCatalog.items.count { it is CatalogItem.Block }
        val noteRange = slots.reserve("native.noteblock", DemoCatalog.NAMESPACE, blockCount, preferredStart = 0)
        return NativeSetup(
            itemCatalog, provider, dataFolder, logger, cmdBase = cmdRange.first,
            zipFileName = "IntegrareDemo-native-pack.zip",
            packDescription = "Integrare Demo Native Item Pack",
            noteBlockStateOffset = noteRange.first
        )
    }

    fun startNativePackHosting(provider: NativeItemProvider, zipFile: File) {
        val hostname = resolveNativeHostname()
        val url = "http://$hostname:$nativePackPort/${zipFile.name}"
        provider.packUrl = url

        if (nativePackServer == null) nativePackServer = NativePackServer(logger)
        nativePackServer!!.start(zipFile, nativePackPort)

        logger.info("Native pack URL: $url")
    }

    private fun resolveNativeHostname(): String {
        val serverIp = server.ip
        if (serverIp.isNotEmpty()) return serverIp
        return try {
            java.net.InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "localhost"
        }
    }

    /** Setup for the active custom item provider, installing [itemCatalog]. */
    fun providerSetup(): ProviderSetup? =
        customItemProviderManager.getProvider()?.let { ProviderSetup.forProvider(it, this, itemCatalog, ::nativeSetup) }
}
