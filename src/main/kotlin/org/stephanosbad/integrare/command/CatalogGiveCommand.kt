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
package org.stephanosbad.integrare.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.stephanosbad.integrare.CatalogItems
import org.stephanosbad.integrare.catalog.ItemCatalog

/**
 * Reusable `/give`-style command: hands a player any item of [catalog], by its
 * bare id (no namespace), looked up through [items].
 *
 * This is the recommended way for an Integrare consumer to expose an admin
 * give-item command, rather than writing one against [ItemCatalog]/[CatalogItems]
 * from scratch. Register an instance under whatever command name, usage and
 * permission node suit your own plugin.yml - nothing here registers itself, so
 * there's no risk of two consumers colliding on the same command name.
 *
 * Usage: `/<command> <item-id> [player]` - player defaults to the sender if
 * they're one; console must name a player.
 *
 * @param permission Permission node required to run this command; null allows anyone
 */
class CatalogGiveCommand(
    private val catalog: ItemCatalog,
    private val items: CatalogItems,
    private val permission: String? = null
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /$label <item-id> [player]").color(NamedTextColor.RED))
            return true
        }

        val id = args[0]
        if (catalog.item(id) == null) {
            sender.sendMessage(
                Component.text("Unknown item id '$id'. Known ids: ${catalog.items.joinToString(", ") { it.id }}")
                    .color(NamedTextColor.RED)
            )
            return true
        }

        val target = when {
            args.size > 1 -> Bukkit.getPlayerExact(args[1])
            sender is Player -> sender
            else -> null
        }
        if (target == null) {
            val message = if (args.size > 1) "Player '${args[1]}' not found or offline." else "Console must specify a player."
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED))
            return true
        }

        val stack = items.get(id)
        if (stack == null) {
            sender.sendMessage(
                Component.text("'$id' isn't available from the active provider yet - has setup been run?")
                    .color(NamedTextColor.YELLOW)
            )
            return true
        }

        val leftover = target.inventory.addItem(stack)
        if (leftover.isNotEmpty()) {
            target.location.world?.dropItemNaturally(target.location, stack)
        }

        sender.sendMessage(Component.text("Gave 1 $id to ${target.name}").color(NamedTextColor.GREEN))
        if (target != sender) {
            target.sendMessage(Component.text("You received 1 $id").color(NamedTextColor.GREEN))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
        when (args.size) {
            1 -> catalog.items.map { it.id }.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
}
