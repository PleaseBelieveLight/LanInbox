package com.luofelix.laninbox.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MESSAGE_ROUTE = "/v1/messages"
const val DEVELOPMENT_PORT = 8765
const val MAX_MESSAGE_BYTES = 64 * 1024
// A single content byte can expand to a six-byte JSON escape. Leave room for the envelope.
const val MAX_REQUEST_BYTES = MAX_MESSAGE_BYTES * 6 + 1024

val MessageJson = Json { ignoreUnknownKeys = true }

@Serializable
enum class MessageKind {
    Text, Link, Code,
}

@Serializable
data class MessageRequest(
    val senderId: String,
    val messageId: String,
    val kind: MessageKind,
    val content: String,
) {
    fun isValid(): Boolean =
        identifier.matches(senderId) && identifier.matches(messageId) &&
            content.isNotBlank() && content.encodeToByteArray().size <= MAX_MESSAGE_BYTES

    private companion object {
        val identifier = Regex("[a-zA-Z0-9_-]{1,64}")
    }
}

@Serializable
data class MessageAcknowledgment(val messageId: String)

@Serializable
data class ProtocolError(val code: String)
