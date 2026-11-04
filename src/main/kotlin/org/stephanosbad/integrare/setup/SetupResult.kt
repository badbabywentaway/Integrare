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

import java.io.File

/**
 * Outcome of installing a catalog into a provider.
 *
 * @property alreadySetup Configuration already existed and nothing was written
 * @property messages Progress lines suitable for showing to an admin
 * @property zipFile Generated resource pack (native provider only)
 * @property packHash SHA-1 of [zipFile] (native provider only)
 */
class SetupResult(
    val success: Boolean,
    val alreadySetup: Boolean,
    val messages: List<String>,
    val zipFile: File? = null,
    val packHash: ByteArray? = null
)
