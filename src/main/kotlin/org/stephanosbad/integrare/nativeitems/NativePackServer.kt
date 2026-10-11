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

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.logging.Logger

/**
 * Lightweight HTTP server that serves the native resource pack zip to clients.
 *
 * Uses the JDK's built-in com.sun.net.httpserver.HttpServer (no extra dependencies).
 * The zip is loaded into memory once at start time to avoid file I/O per request.
 *
 * Start with start(); stop with stop() on plugin disable.
 */
class NativePackServer(private val logger: Logger) {

    private var server: HttpServer? = null

    val isRunning: Boolean get() = server != null

    fun start(zipFile: File, port: Int) {
        stop()

        val zipBytes = zipFile.readBytes()
        val filename = zipFile.name

        try {
            val httpServer = HttpServer.create(InetSocketAddress(port), 0)
            httpServer.createContext("/$filename") { exchange ->
                exchange.responseHeaders.set("Content-Type", "application/zip")
                exchange.sendResponseHeaders(200, zipBytes.size.toLong())
                exchange.responseBody.use { it.write(zipBytes) }
            }
            httpServer.executor = null
            httpServer.start()
            server = httpServer
            logger.info("NativePackServer started on port $port — serving $filename")
        } catch (e: Exception) {
            logger.severe("NativePackServer failed to start on port $port: ${e.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}
