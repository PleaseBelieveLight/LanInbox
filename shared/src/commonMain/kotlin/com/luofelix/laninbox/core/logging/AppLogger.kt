package com.luofelix.laninbox.core.logging

/**
 * Sends English diagnostic events to a platform log sink.
 *
 * Keep message bodies, clipboard text, full URLs, pairing codes, credentials, and local file
 * paths out of events, including when reporting exceptions.
 */
fun interface AppLogger {
    fun info(event: String)
}
