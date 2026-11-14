/*
 * Integrare demo plugin - exercises the library's own functionality
 * Copyright (C) 2025 StephanosBad
 */
package org.stephanosbad.integrare.demo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.BlockFace
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.stephanosbad.integrare.command.CatalogGiveCommand

/**
 * `/integraredemo <setup|give|furniture> [args]` - everything this demo plugin
 * does, in one command:
 * - `setup [force]` runs [ProviderSetup][org.stephanosbad.integrare.setup.ProviderSetup]
 *   for whichever provider is installed
 * - `give <id> [player]` delegates to [CatalogGiveCommand]
 * - `furniture <place|remove>` exercises placeFurniture/getFurniture/removeFurniture directly
 */
class DemoCommand(
    private val plugin: IntegrareDemoPlugin,
    private val giveCommand: CatalogGiveCommand
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("integraredemo.admin")) {
            sender.sendMessage(Component.text("You don't have permission.").color(NamedTextColor.RED))
            return true
        }
        when (args.getOrNull(0)?.lowercase()) {
            "setup" -> runSetup(sender, args.getOrNull(1)?.equals("force", ignoreCase = true) == true)
            "give" -> giveCommand.onCommand(sender, command, label, args.drop(1).toTypedArray())
            "furniture" -> handleFurniture(sender, args.getOrNull(1)?.lowercase())
            else -> sender.sendMessage(Component.text("Usage: /$label <setup|give|furniture> [args]").color(NamedTextColor.RED))
        }
        return true
    }

    private fun runSetup(sender: CommandSender, force: Boolean) {
        val setup = plugin.providerSetup()
        if (setup == null) {
            sender.sendMessage(
                Component.text("No setup needed/available for this provider (the native provider sets itself up on enable).")
                    .color(NamedTextColor.YELLOW)
            )
            return
        }
        val result = setup.install(force)
        result.messages.forEach { sender.sendMessage(Component.text(it)) }
        if (result.success) plugin.catalogItems.invalidate()
    }

    private fun handleFurniture(sender: CommandSender, action: String?) {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only a player can do this.").color(NamedTextColor.RED))
            return
        }
        val provider = plugin.customItemProviderManager.getProvider()
        if (provider == null) {
            sender.sendMessage(Component.text("No provider available.").color(NamedTextColor.RED))
            return
        }
        when (action) {
            "place" -> {
                val item = plugin.itemCatalog.item("ruby_chair")
                    ?: return sender.sendMessage(Component.text("ruby_chair isn't in the catalog.").color(NamedTextColor.RED))
                val namespacedId = plugin.itemCatalog.namespacedId(item)
                val entity = provider.placeFurniture(namespacedId, sender.location, yawToBlockFace(sender.location.yaw))
                if (entity != null) {
                    sender.sendMessage(Component.text("Placed a ruby chair.").color(NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(
                        Component.text("Failed to place furniture - has setup been run?").color(NamedTextColor.RED)
                    )
                }
            }
            "remove" -> {
                val target = sender.getTargetEntity(10)
                if (target == null) {
                    sender.sendMessage(Component.text("No entity in sight.").color(NamedTextColor.YELLOW))
                    return
                }
                val info = provider.getFurniture(target)
                if (info == null) {
                    sender.sendMessage(Component.text("That's not furniture.").color(NamedTextColor.YELLOW))
                    return
                }
                provider.removeFurniture(target)
                sender.sendMessage(Component.text("Removed ${info.furnitureName}.").color(NamedTextColor.GREEN))
            }
            else -> sender.sendMessage(Component.text("Usage: /integraredemo furniture <place|remove>").color(NamedTextColor.RED))
        }
    }

    private fun yawToBlockFace(yaw: Float): BlockFace {
        val normalized = ((yaw % 360) + 360) % 360
        return when {
            normalized < 45 || normalized >= 315 -> BlockFace.SOUTH
            normalized < 135 -> BlockFace.WEST
            normalized < 225 -> BlockFace.NORTH
            else -> BlockFace.EAST
        }
    }
}
