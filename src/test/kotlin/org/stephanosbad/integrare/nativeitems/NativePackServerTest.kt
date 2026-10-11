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
package org.stephanosbad.integrare.nativeitems

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.util.logging.Logger

class NativePackServerTest {

    @TempDir
    lateinit var tempDir: File

    private val server = NativePackServer(Logger.getLogger("NativePackServerTest"))

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `serves the pack zip under its file name`() {
        val content = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)
        val zip = File(tempDir, "test-pack.zip").apply { writeBytes(content) }
        val port = freePort()

        server.start(zip, port)

        assertTrue(server.isRunning)
        val connection = URI("http://localhost:$port/test-pack.zip").toURL().openConnection() as HttpURLConnection
        assertEquals(200, connection.responseCode)
        assertEquals("application/zip", connection.contentType)
        assertArrayEquals(content, connection.inputStream.use { it.readBytes() })
    }

    @Test
    fun `stop shuts the server down`() {
        val zip = File(tempDir, "test-pack.zip").apply { writeBytes(byteArrayOf(1)) }

        server.start(zip, freePort())
        server.stop()

        assertFalse(server.isRunning)
    }

    @Test
    fun `port already in use leaves the server not running`() {
        val zip = File(tempDir, "test-pack.zip").apply { writeBytes(byteArrayOf(1)) }

        ServerSocket(0).use { occupied ->
            server.start(zip, occupied.localPort)
        }

        assertFalse(server.isRunning)
    }
}
