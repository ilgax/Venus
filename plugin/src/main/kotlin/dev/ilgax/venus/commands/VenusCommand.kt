package dev.ilgax.venus.commands

import dev.ilgax.venus.VenusPlugin
import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.handlers.AuthHandler
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.ConsoleCommandSender

class VenusCommand(
    private val plugin: VenusPlugin,
    private val authHandler: AuthHandler,
) : BasicCommand {
    override fun execute(
        stack: CommandSourceStack,
        args: Array<String>,
    ) {
        val sender = stack.sender

        if (args.isEmpty()) {
            sender.sendMessage("Usage: venus allow | venus deny | venus reload")
            return
        }

        when (args[0].lowercase()) {
            "allow", "deny" -> {
                if (sender !is ConsoleCommandSender) {
                    sender.sendMessage("This command can only be run from the console.")
                    return
                }
                when (args[0].lowercase()) {
                    "allow" -> handleAllow(sender)
                    "deny" -> handleDeny(sender)
                }
            }

            "reload" -> {
                if (sender !is ConsoleCommandSender && !sender.hasPermission("venus.reload")) {
                    sender.sendMessage("You don't have permission to use this command.")
                    return
                }
                VenusConfig.load(plugin)
                sender.sendMessage("Venus config reloaded.")
                plugin.logger.info("Venus config reloaded by ${sender.name}.")
            }

            else -> {
                sender.sendMessage("Unknown subcommand. Use allow, deny, or reload.")
            }
        }
    }

    private fun handleAllow(sender: org.bukkit.command.CommandSender) {
        val entry = SessionManager.getNextPendingApproval()
        if (entry == null) {
            sender.sendMessage("No pending Venus requests.")
            return
        }
        val (uuid, approval) = entry
        val player = plugin.server.getPlayer(uuid)
        if (player == null) {
            SessionManager.removePendingApproval(uuid)
            sender.sendMessage("Player is no longer online.")
            return
        }
        AuthorizedKeys.authorize(approval.clientPublicKeyBase64, player.name)
        SessionManager.removePendingApproval(uuid)
        authHandler.startApprovedChallenge(player, approval.clientPublicKey)
        sender.sendMessage("${player.name} authorized.")
        plugin.logger.info("${player.name} authorized via console.")
    }

    private fun handleDeny(sender: org.bukkit.command.CommandSender) {
        val entry = SessionManager.getNextPendingApproval()
        if (entry == null) {
            sender.sendMessage("No pending Venus requests.")
            return
        }
        val (uuid, _) = entry
        val player = plugin.server.getPlayer(uuid)
        val playerName = player?.name ?: uuid.toString()
        SessionManager.removePendingApproval(uuid)
        if (player != null) {
            authHandler.notifyDenied(player)
        }
        sender.sendMessage("$playerName denied.")
    }
}
