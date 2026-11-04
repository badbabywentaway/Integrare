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

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Shared, append-only registry of which numeric slot range each plugin's
 * namespace occupies in a manually-numbered space: Oraxen's noteblock
 * `custom_variation`, or the native provider's custom model data and note
 * block state ranges (two independent spaces of their own). Neither Oraxen
 * nor the native provider assigns those numbers for you across unrelated
 * plugins the way ItemsAdder and Nexo do internally, so without this, two
 * Integrare-based plugins on the same server would both number their own
 * items from zero and collide.
 *
 * Every plugin that bundles Integrare shares the same backing file, at a path
 * outside any one plugin's own data folder, so this works without any plugin
 * depending on another or anything being installed separately - they're all
 * running in the same server process and can share an ordinary file.
 *
 * A namespace's range never moves once assigned, including across restarts -
 * already-placed blocks and items depend on that number staying put - so
 * [reserve] is idempotent and meant to be called every boot, not just once.
 *
 * @param file The shared backing file; use the [Plugin] constructor to default
 *   to the conventional shared path
 */
class SlotRegistry(private val file: File) {

    /** Stores the registry at a path shared by every plugin on the server, not [plugin]'s own data folder. */
    constructor(plugin: Plugin) : this(File(plugin.server.pluginsFolder, "Integrare/slots.yml"))

    /**
     * Returns [namespace]'s existing range in [space], or reserves and persists
     * a new [count]-wide range.
     *
     * A fresh reservation tries [preferredStart] first (for a plugin migrating
     * numbers it already assigned itself before using this registry), falling
     * back to the next free range after everything already claimed in [space]
     * if [preferredStart] would overlap another namespace's range, or if it's
     * null.
     *
     * @param space Identifies which numeric space this is, e.g.
     *   "oraxen.variation", "native.cmd", "native.noteblock" - each space
     *   tracks its namespaces independently of the others
     */
    @Synchronized
    fun reserve(space: String, namespace: String, count: Int, preferredStart: Int? = null): IntRange {
        val yaml = load()
        val section = yaml.getConfigurationSection(space) ?: yaml.createSection(space)

        section.getConfigurationSection(namespace)?.let {
            val start = it.getInt("start")
            val existingCount = it.getInt("count")
            return start until (start + existingCount)
        }

        val claimed = section.getKeys(false).map { key ->
            val start = section.getInt("$key.start")
            val existingCount = section.getInt("$key.count")
            start until (start + existingCount)
        }

        val range = preferredStart
            ?.let { it until (it + count) }
            ?.takeIf { candidate -> claimed.none { it.overlaps(candidate) } }
            ?: run {
                val next = claimed.maxOfOrNull { it.last + 1 } ?: 0
                next until (next + count)
            }

        section.set("$namespace.start", range.first)
        section.set("$namespace.count", count)
        save(yaml)
        return range
    }

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last

    private fun load(): YamlConfiguration =
        if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()

    private fun save(yaml: YamlConfiguration) {
        file.parentFile?.mkdirs()
        yaml.save(file)
    }
}
