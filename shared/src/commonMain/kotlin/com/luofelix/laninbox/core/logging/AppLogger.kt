package com.luofelix.laninbox.core.logging

/**
 * Sends English diagnostic events to a platform log sink.
 *
 * Keep message bodies, clipboard text, full URLs, pairing codes, credentials, and local file
 * paths out of events, including when reporting exceptions.
 */
fun interface AppLogger {
    fun log(event: LogEvent)

    fun info(message: String, vararg fields: Pair<String, Any>) =
        log(LogEvent(LogLevel.Info, message, fields.toMap()))

    fun warn(message: String, vararg fields: Pair<String, Any>) =
        log(LogEvent(LogLevel.Warning, message, fields.toMap()))
}

enum class LogLevel { Info, Warning }

class LogEvent(val level: LogLevel, val message: String, val fields: Map<String, Any>) {
    fun render(): String = buildString {
        append(message)
        fields.forEach { (key, value) -> append(" $key=$value") }
    }
}
