/*
 * Integrare demo plugin - exercises the library's own functionality
 * Copyright (C) 2025 StephanosBad
 */
package org.stephanosbad.integrare.demo

import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player

/** The horizontal center of the block under [player]'s feet, at that block's floor level. */
internal fun blockCenter(player: Player): Location {
    val feet = player.location.block
    return Location(feet.world, feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5, player.location.yaw, 0f)
}

internal fun yawToBlockFace(yaw: Float): BlockFace {
    val normalized = ((yaw % 360) + 360) % 360
    return when {
        normalized < 45 || normalized >= 315 -> BlockFace.SOUTH
        normalized < 135 -> BlockFace.WEST
        normalized < 225 -> BlockFace.NORTH
        else -> BlockFace.EAST
    }
}
