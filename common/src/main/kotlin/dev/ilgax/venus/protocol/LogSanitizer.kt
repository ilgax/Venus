package dev.ilgax.venus.protocol

object LogSanitizer {
    fun sanitize(s: String): String =
        s
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .filter { it.code >= 0x20 || it == ' ' }

    fun redactCommand(command: String): String = sanitize(command.substringBefore(' '))
}
