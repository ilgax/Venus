package dev.ilgax.venus.commands

import dev.ilgax.venus.VenusPlugin
import dev.ilgax.venus.backend.BackendApprovalService
import dev.ilgax.venus.config.VenusConfig
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.ConsoleCommandSender

class VenusCommand(
    private val plugin: VenusPlugin,
    private val approvals: BackendApprovalService,
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
        sender.sendMessage(approvals.allowNextPending().message)
    }

    private fun handleDeny(sender: org.bukkit.command.CommandSender) {
        sender.sendMessage(approvals.denyNextPending().message)
    }
}
