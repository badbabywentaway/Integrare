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

import org.bukkit.plugin.Plugin
import org.stephanosbad.integrare.CustomItemProvider
import org.stephanosbad.integrare.ProviderKind
import org.stephanosbad.integrare.catalog.ItemCatalog
import org.stephanosbad.integrare.provider.ItemsAdderProvider
import org.stephanosbad.integrare.provider.NativeItemProvider
import org.stephanosbad.integrare.provider.NexoProvider
import org.stephanosbad.integrare.provider.OraxenProvider
import java.io.File

/**
 * Installs an [ItemCatalog] into one custom item provider.
 */
interface ProviderSetup {
    val kind: ProviderKind

    /** Whether the catalog has already been installed */
    fun isAlreadySetup(): Boolean

    /**
     * Installs the catalog. Does nothing if [isAlreadySetup], unless [force]
     * is set, in which case the previous installation is removed first.
     */
    fun install(force: Boolean = false): SetupResult

    /** What an admin has to do after installing, e.g. reload the provider */
    val nextSteps: List<String>

    /** Server settings the provider needs, e.g. paper-global.yml options */
    val serverRequirements: List<String> get() = emptyList()

    companion object {
        /** paper-global.yml settings for note block based custom blocks */
        val NOTE_BLOCK_REQUIREMENTS = listOf(
            "In config/paper-global.yml set:",
            "  block-updates:",
            "    disable-noteblock-updates: true"
        )

        /**
         * The setup for [provider], the provider chosen by CustomItemProviderManager.
         *
         * @param nativeSetup Builds the native setup, which needs plugin-specific settings
         */
        fun forProvider(
            provider: CustomItemProvider,
            plugin: Plugin,
            catalog: ItemCatalog,
            nativeSetup: (NativeItemProvider) -> NativeSetup
        ): ProviderSetup? {
            fun folder(name: String): File? = plugin.server.pluginManager.getPlugin(name)?.dataFolder
            return when (provider) {
                is NativeItemProvider -> nativeSetup(provider)
                is ItemsAdderProvider -> folder("ItemsAdder")?.let { ItemsAdderSetup(catalog, it, plugin.logger, plugin.name) }
                is OraxenProvider -> folder("Oraxen")?.let { OraxenSetup(catalog, it, plugin.logger, plugin.name) }
                is NexoProvider -> folder("Nexo")?.let { NexoSetup(catalog, it, plugin.logger, plugin.name) }
                else -> null
            }
        }
    }
}
