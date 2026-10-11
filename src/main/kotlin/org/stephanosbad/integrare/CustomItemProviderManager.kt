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
package org.stephanosbad.integrare

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.stephanosbad.integrare.provider.ItemsAdderProvider
import org.stephanosbad.integrare.provider.NativeItemProvider
import org.stephanosbad.integrare.provider.NexoProvider
import org.stephanosbad.integrare.provider.OraxenProvider

/**
 * Manager for custom item providers
 *
 * Handles detection and initialization of ItemsAdder, Oraxen, or Nexo, falling back
 * to the built-in Native provider when none of them is installed.
 * Enforces that at most one external provider is present.
 *
 * @param plugin The plugin using this library (used for logging and messages)
 * @param nativeNbtKey NBT key the Native provider uses to tag its items
 */
class CustomItemProviderManager(
    private val plugin: Plugin,
    private val nativeNbtKey: String = NativeItemProvider.DEFAULT_NBT_KEY
) {

    private var provider: CustomItemProvider? = null

    /**
     * Result of provider initialization
     */
    data class InitResult(
        val success: Boolean,
        val provider: CustomItemProvider?,
        val messages: List<String>
    )

    /**
     * Initializes the custom item provider
     *
     * Checks which custom item plugins are available and initializes the appropriate provider.
     * Enforces that at most one of ItemsAdder, Oraxen, or Nexo is present; with none installed
     * the built-in Native provider is used.
     *
     * @return InitResult containing success status, provider instance, and messages
     */
    fun initialize(): InitResult {
        val messages = mutableListOf<String>()

        // Debug: List all loaded plugins
        val loadedPlugins = Bukkit.getPluginManager().plugins.map { it.name }
        plugin.logger.info("Loaded plugins at initialization: ${loadedPlugins.joinToString(", ")}")

        val itemsAdderAvailable = isPluginAvailable("ItemsAdder")
        val oraxenAvailable = isPluginAvailable("Oraxen")
        val nexoAvailable = isPluginAvailable("Nexo")

        // Count how many providers are available
        val providersCount = listOf(itemsAdderAvailable, oraxenAvailable, nexoAvailable).count { it }

        // Check that at most one provider is present
        when {
            providersCount > 1 -> {
                val installedProviders = mutableListOf<String>()
                if (itemsAdderAvailable) installedProviders.add("ItemsAdder")
                if (oraxenAvailable) installedProviders.add("Oraxen")
                if (nexoAvailable) installedProviders.add("Nexo")

                messages.add("ERROR: Multiple custom item plugins detected: ${installedProviders.joinToString(", ")}")
                messages.add("${plugin.name} requires exactly ONE custom item plugin.")
                messages.add("Please keep only one and restart the server.")
                plugin.logger.severe("========================================")
                plugin.logger.severe("FATAL: Multiple custom item plugins detected!")
                plugin.logger.severe("Found: ${installedProviders.joinToString(", ")}")
                plugin.logger.severe("${plugin.name} cannot run with multiple providers.")
                plugin.logger.severe("Please remove all but one and restart the server.")
                plugin.logger.severe("========================================")
                return InitResult(false, null, messages)
            }

            providersCount == 0 -> {
                try {
                    provider = NativeItemProvider(nativeNbtKey)
                    messages.add("No external item plugin detected — using built-in NativeItemManager")
                    plugin.logger.info("========================================")
                    plugin.logger.info("Custom Item Provider: NativeItems (built-in fallback)")
                    plugin.logger.info("ItemsAdder, Oraxen, and Nexo are not installed.")
                    plugin.logger.info("Items will use vanilla PAPER with custom model data.")
                    plugin.logger.info("========================================")
                    return InitResult(true, provider, messages)
                } catch (e: Exception) {
                    messages.add("ERROR: Failed to initialize native item provider: ${e.message}")
                    plugin.logger.severe("Failed to initialize native item provider: ${e.message}")
                    e.printStackTrace()
                    return InitResult(false, null, messages)
                }
            }

            itemsAdderAvailable -> {
                try {
                    provider = ItemsAdderProvider()
                    messages.add("Using ItemsAdder as custom item provider")
                    plugin.logger.info("========================================")
                    plugin.logger.info("Custom Item Provider: ItemsAdder")
                    plugin.logger.info("========================================")
                    return InitResult(true, provider, messages)
                } catch (e: Exception) {
                    messages.add("ERROR: Failed to initialize ItemsAdder provider: ${e.message}")
                    plugin.logger.severe("Failed to initialize ItemsAdder provider: ${e.message}")
                    e.printStackTrace()
                    return InitResult(false, null, messages)
                }
            }

            oraxenAvailable -> {
                try {
                    provider = OraxenProvider()
                    messages.add("Using Oraxen as custom item provider")
                    plugin.logger.info("========================================")
                    plugin.logger.info("Custom Item Provider: Oraxen")
                    plugin.logger.info("========================================")
                    return InitResult(true, provider, messages)
                } catch (e: Exception) {
                    messages.add("ERROR: Failed to initialize Oraxen provider: ${e.message}")
                    plugin.logger.severe("Failed to initialize Oraxen provider: ${e.message}")
                    e.printStackTrace()
                    return InitResult(false, null, messages)
                }
            }

            nexoAvailable -> {
                try {
                    provider = NexoProvider()
                    messages.add("Using Nexo as custom item provider")
                    plugin.logger.info("========================================")
                    plugin.logger.info("Custom Item Provider: Nexo")
                    plugin.logger.info("⚠ IMPORTANT: Enable Paper block update optimizations!")
                    plugin.logger.info("========================================")
                    return InitResult(true, provider, messages)
                } catch (e: Exception) {
                    messages.add("ERROR: Failed to initialize Nexo provider: ${e.message}")
                    plugin.logger.severe("Failed to initialize Nexo provider: ${e.message}")
                    e.printStackTrace()
                    return InitResult(false, null, messages)
                }
            }

            else -> {
                messages.add("ERROR: Unexpected state during provider initialization")
                return InitResult(false, null, messages)
            }
        }
    }

    /**
     * Gets the initialized custom item provider
     *
     * @return The provider instance, or null if not initialized
     */
    fun getProvider(): CustomItemProvider? = provider

    /**
     * Checks if the provider is initialized and ready to use
     *
     * @return true if provider is available
     */
    fun isReady(): Boolean = provider != null

    /**
     * Checks if a plugin is loaded and enabled
     *
     * @param pluginName The name of the plugin
     * @return true if the plugin is loaded and enabled
     */
    private fun isPluginAvailable(pluginName: String): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(pluginName)
        val isAvailable = plugin != null && plugin.isEnabled

        // Debug logging
        if (plugin == null) {
            this.plugin.logger.info("Plugin '$pluginName' not found in plugin manager")
        } else if (!plugin.isEnabled) {
            this.plugin.logger.info("Plugin '$pluginName' found but not enabled (state: ${plugin.isEnabled})")
        } else {
            this.plugin.logger.info("Plugin '$pluginName' detected and enabled")
        }

        return isAvailable
    }

    /**
     * Gets the name of the active provider
     *
     * @return Provider name (e.g., "ItemsAdder", "Oraxen", "Nexo", "NativeItems"), or "None" if not initialized
     */
    fun getProviderName(): String {
        return provider?.getProviderName() ?: "None"
    }
}
