package dev.xcyn.venus.commands

import dev.xcyn.venus.VenusPlugin
import dev.xcyn.venus.auth.AuthorizedKeys
import dev.xcyn.venus.auth.Handshake
import dev.xcyn.venus.auth.PendingSession
import dev.xcyn.venus.auth.SessionManager
import dev.xcyn.venus.config.VenusConfig
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.ConsoleCommandSender

class VenusCommand(private val plugin: VenusPlugin) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
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
                if (sender !is ConsoleCommandSender && !sender.isPermissionSet("venus.reload")) {
                    sender.sendMessage("You don't have permission to use this command.")
                    return
                }
                VenusConfig.load(plugin)
                sender.sendMessage("Venus config reloaded.")
                plugin.logger.info("Venus config reloaded by ${sender.name}.")
            }
            else -> sender.sendMessage("Unknown subcommand. Use allow, deny, or reload.")
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
        val challenge = Handshake.generateChallenge()
        val serverSig = Handshake.sign(challenge, plugin.keyManager.privateKey)
        SessionManager.addPending(uuid, PendingSession(approval.clientPublicKey, challenge))
        plugin.sendAuthChallengeTo(player, challenge, serverSig)
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
        val playerName = plugin.server.getPlayer(uuid)?.name ?: uuid.toString()
        SessionManager.removePendingApproval(uuid)
        sender.sendMessage("$playerName denied.")
    }
}