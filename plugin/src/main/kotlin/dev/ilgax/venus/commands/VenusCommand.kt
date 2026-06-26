package dev.ilgax.venus.commands

import dev.ilgax.venus.VenusPlugin
import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.backend.BackendApprovalService
import dev.ilgax.venus.config.VenusConfig
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.ConsoleCommandSender

class VenusCommand(
    private val plugin: VenusPlugin,
    private val approvals: BackendApprovalService,
) : BasicCommand {
    override fun permission(): String = "venus.admin"

    override fun execute(
        stack: CommandSourceStack,
        args: Array<String>,
    ) {
        val sender = stack.sender

        if (args.isEmpty()) {
            sender.sendMessage("Usage: venus allow | venus deny | venus reload | venus list | venus revoke <fingerprint>")
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

            "list" -> {
                if (sender !is ConsoleCommandSender) {
                    sender.sendMessage("This command can only be run from the console.")
                    return
                }
                handleList(sender)
            }

            "revoke" -> {
                if (sender !is ConsoleCommandSender) {
                    sender.sendMessage("This command can only be run from the console.")
                    return
                }
                if (args.size < 2) {
                    sender.sendMessage("Usage: venus revoke <fingerprint>")
                    return
                }
                handleRevoke(sender, args[1])
            }

            else -> {
                sender.sendMessage("Unknown subcommand. Use allow, deny, reload, list, or revoke.")
            }
        }
    }

    private fun handleAllow(sender: org.bukkit.command.CommandSender) {
        sender.sendMessage(approvals.allowNextPending().message)
    }

    private fun handleDeny(sender: org.bukkit.command.CommandSender) {
        sender.sendMessage(approvals.denyNextPending().message)
    }

    private fun handleList(sender: org.bukkit.command.CommandSender) {
        val entries = AuthorizedKeys.list()
        if (entries.isEmpty()) {
            sender.sendMessage("No authorized Venus keys.")
        } else {
            sender.sendMessage("Authorized Venus keys (${entries.size}):")
            entries.forEach { entry ->
                val claimed = if (entry.comment.isEmpty()) "(no claimed name)" else "(claimed: ${entry.comment})"
                sender.sendMessage("  ${entry.fingerprint}  $claimed")
            }
        }
    }

    private fun handleRevoke(
        sender: org.bukkit.command.CommandSender,
        target: String,
    ) {
        if (!target.startsWith("SHA256:")) {
            sender.sendMessage(
                "Revocation by name is not supported (names are spoofable in offline mode). Run 'venus list' to see key fingerprints.",
            )
            return
        }
        val removed = AuthorizedKeys.removeEntryByFingerprint(target)
        if (removed != null) {
            val deactivatedSessions = approvals.deactivateSessionsForKey(removed.publicKeyBase64)
            sender.sendMessage("Revoked Venus key $target.")
            plugin.logger.info("Venus key revoked by ${sender.name}: $target; deactivated $deactivatedSessions active session(s)")
        } else {
            sender.sendMessage("No authorized Venus key found for $target.")
        }
    }
}
