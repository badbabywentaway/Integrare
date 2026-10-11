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

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.stephanosbad.integrare.provider.NativeItemProvider
import java.util.logging.Logger

/**
 * Unit tests for CustomItemProviderManager
 *
 * Provider detection runs against a mocked Bukkit plugin manager. The external
 * provider classes themselves are never instantiated here (their plugin APIs are
 * not on the test classpath), so only the Native fallback and the conflict paths
 * are exercised end to end.
 */
class CustomItemProviderManagerTest {

    // A real Logger: mocking java.util.logging.Logger crashes under JDK 25
    private val owner = mockk<Plugin> {
        every { name } returns "TestPlugin"
        every { logger } returns Logger.getLogger("CustomItemProviderManagerTest")
    }

    // Static mocking of Bukkit does not take effect under JDK 25, so a mocked Server
    // is installed directly into Bukkit's private static field instead.
    private val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }

    @AfterEach
    fun tearDown() {
        serverField.set(null, null)
    }

    private fun installedPlugins(vararg enabled: Pair<String, Boolean>) {
        val plugins = enabled.associate { (pluginName, isEnabled) ->
            pluginName to mockk<Plugin> {
                every { name } returns pluginName
                every { this@mockk.isEnabled } returns isEnabled
            }
        }
        val pluginManager = mockk<PluginManager> {
            every { this@mockk.plugins } returns plugins.values.toTypedArray()
            every { getPlugin(any()) } answers { plugins[firstArg<String>()] }
        }
        serverField.set(null, mockk<Server> { every { getPluginManager() } returns pluginManager })
    }

    @Test
    fun `manager is not ready before initialization`() {
        val manager = CustomItemProviderManager(owner)

        assertFalse(manager.isReady())
        assertNull(manager.getProvider())
        assertEquals("None", manager.getProviderName())
    }

    @Test
    fun `falls back to native provider when no external plugin is installed`() {
        installedPlugins()
        val manager = CustomItemProviderManager(owner)

        val result = manager.initialize()

        assertTrue(result.success)
        assertInstanceOf(NativeItemProvider::class.java, result.provider)
        assertSame(result.provider, manager.getProvider())
        assertTrue(manager.isReady())
        assertEquals("NativeItems", manager.getProviderName())
    }

    @Test
    fun `disabled external plugin does not count as a provider`() {
        installedPlugins("Oraxen" to false)

        val result = CustomItemProviderManager(owner).initialize()

        assertTrue(result.success)
        assertInstanceOf(NativeItemProvider::class.java, result.provider)
    }

    @ParameterizedTest
    @CsvSource(
        "ItemsAdder, Oraxen",
        "ItemsAdder, Nexo",
        "Oraxen, Nexo"
    )
    fun `multiple external providers fail initialization`(first: String, second: String) {
        installedPlugins(first to true, second to true)
        val manager = CustomItemProviderManager(owner)

        val result = manager.initialize()

        assertFalse(result.success)
        assertNull(result.provider)
        assertFalse(manager.isReady())
        assertTrue(result.messages.any { first in it && second in it },
            "Conflict message should name both plugins: ${result.messages}")
        assertTrue(result.messages.any { "TestPlugin" in it },
            "Conflict message should name the owning plugin: ${result.messages}")
    }

    @Test
    fun `all three external providers fail initialization`() {
        installedPlugins("ItemsAdder" to true, "Oraxen" to true, "Nexo" to true)

        val result = CustomItemProviderManager(owner).initialize()

        assertFalse(result.success)
        assertNull(result.provider)
    }
}
