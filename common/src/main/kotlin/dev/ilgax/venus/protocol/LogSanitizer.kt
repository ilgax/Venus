package dev.ilgax.venus.protocol

object LogSanitizer {
    fun sanitize(s: String): String =
        s
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .filter { it.code >= 0x20 && it.code != 0x7F && it.code != 0x2028 && it.code != 0x2029 }

    fun redactCommand(command: String): String = sanitize(command.trimStart().takeWhile { !it.isWhitespace() })
}
