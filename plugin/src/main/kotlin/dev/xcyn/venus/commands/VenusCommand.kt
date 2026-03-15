package dev.xcyn.venus.commands

import dev.xcyn.venus.VenusPlugin
import dev.xcyn.venus.auth.AuthorizedKeys
import dev.xcyn.venus.auth.Handshake
import dev.xcyn.venus.auth.PendingSession
import dev.xcyn.venus.auth.SessionManager
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.ConsoleCommandSender

class VenusCommand(private val plugin: VenusPlugin) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (sender !is ConsoleCommandSender) {
            sender.sendMessage("This command can only be run from the console.")
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /venus allow | /venus deny")
            return
        }

        when (args[0].lowercase()) {
            "allow" -> {
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
            "deny" -> {
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
            else -> sender.sendMessage("Unknown subcommand. Use allow or deny.")
        }
    }
}