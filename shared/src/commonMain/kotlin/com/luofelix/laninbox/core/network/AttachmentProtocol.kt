package com.luofelix.laninbox.core.network

import kotlinx.serialization.Serializable

const val MAX_ATTACHMENT_BYTES = 100L * 1024 * 1024
const val ATTACHMENT_SIZE_HEADER = "X-LanInbox-Size"
const val ATTACHMENT_HASH_HEADER = "X-LanInbox-SHA256"
const val ATTACHMENT_NAME_HEADER = "X-LanInbox-Name"
const val ATTACHMENT_TYPE_HEADER = "X-LanInbox-Type"

private val attachmentId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val digest = Regex("[0-9a-f]{64}")
private val mimeType = Regex("[a-zA-Z0-9!#$&^_.+-]{1,64}/[a-zA-Z0-9!#$&^_.+-]{1,64}")

@Serializable
data class AttachmentInfo(
    val id: String,
    val size: Long,
    val sha256: String,
    val name: String = "attachment",
    val mediaType: String = "application/octet-stream",
) {
    fun isValid(): Boolean = attachmentId.matches(id) && size in 0..MAX_ATTACHMENT_BYTES &&
        digest.matches(sha256) && name == safeAttachmentName(name) && mimeType.matches(mediaType)
}

@Serializable
data class OutboundDelivery(
    val id: String,
    val message: MessageRequest? = null,
    val attachment: AttachmentInfo? = null,
) {
    fun isValid(): Boolean = attachmentId.matches(id) && when {
        message != null && attachment == null -> message.messageId == id && message.isValid()
        message == null && attachment != null -> attachment.id == id && attachment.isValid()
        else -> false
    }
}

fun safeAttachmentName(name: String): String = name.map {
    if (it.code < 32 || it.code == 127 || it in "/\\<>:\"|?*") '_' else it
}.joinToString("").take(120).trim { it == '.' || it.isWhitespace() }.ifEmpty { "attachment" }

fun attachmentMediaType(type: String?): String =
    type?.takeIf(mimeType::matches) ?: "application/octet-stream"

/** Accepts numeric RFC 1918 addresses and an optional port without DNS or URI interpretation. */
fun privateLanAuthority(address: String, defaultPort: Int): String? {
    val parts = address.trim().split(':')
    if (parts.size > 2) return null
    val octets = parts.first().split('.')
    if (octets.size != 4 || octets.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return null
    val numbers = octets.map(String::toInt)
    if (numbers.any { it !in 0..255 }) return null
    val (first, second) = numbers
    if (first != 10 && (first != 172 || second !in 16..31) && (first != 192 || second != 168)) return null
    val port = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else defaultPort
    return "${parts.first()}:$port".takeIf { port in 1..65535 }
}
