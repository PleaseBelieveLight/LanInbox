package com.luofelix.laninbox.feature.send

import com.luofelix.laninbox.core.network.DEVELOPMENT_PORT
import com.luofelix.laninbox.core.network.MESSAGE_ROUTE
import com.luofelix.laninbox.core.network.MessageAcknowledgment
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.MessageRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException

enum class DeliveryResult {
    Delivered, Timeout, Unreachable, Rejected, InvalidResponse, PermissionRequired,
}

fun interface MessageSender {
    /** Returns [DeliveryResult.Delivered] after the receiver acknowledges this message ID. */
    suspend fun send(address: String, message: MessageRequest): DeliveryResult
}

class DevelopmentMessageSender(
    private val client: HttpClient = HttpClient {
        // Keep cleartext requests on the validated private IPv4 endpoint.
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 3_000
            requestTimeoutMillis = 8_000
        }
    },
) : MessageSender {
    override suspend fun send(address: String, message: MessageRequest): DeliveryResult {
        val endpoint = developmentEndpoint(address) ?: return DeliveryResult.Rejected
        return try {
            withTimeoutOrNull(8_000) {
                client.preparePost(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(MessageJson.encodeToString(message))
                }.execute { response ->
                    if (response.status != HttpStatusCode.OK) return@execute DeliveryResult.Rejected
                    // Read one extra byte to detect an oversized acknowledgment.
                    val bytes = response.bodyAsChannel().readRemaining(1025).readByteArray()
                    if (bytes.size > 1024) return@execute DeliveryResult.InvalidResponse
                    val acknowledgment = MessageJson.decodeFromString<MessageAcknowledgment>(bytes.decodeToString())
                    if (acknowledgment.messageId == message.messageId) DeliveryResult.Delivered
                    else DeliveryResult.InvalidResponse
                }
            } ?: DeliveryResult.Timeout
        } catch (_: HttpRequestTimeoutException) {
            DeliveryResult.Timeout
        } catch (_: ConnectTimeoutException) {
            DeliveryResult.Timeout
        } catch (_: SocketTimeoutException) {
            DeliveryResult.Timeout
        } catch (_: SerializationException) {
            DeliveryResult.InvalidResponse
        } catch (_: IOException) {
            DeliveryResult.Unreachable
        }
    }

    fun close() = client.close()
}

internal fun developmentEndpoint(address: String): String? {
    val parts = address.trim().split(':')
    if (parts.size > 2) return null
    val octets = parts.first().split('.')
    if (octets.size != 4 || octets.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return null
    val numbers = octets.map(String::toInt)
    if (numbers.any { it !in 0..255 }) return null
    val (first, second) = numbers
    val privateAddress = first == 10 || first == 172 && second in 16..31 ||
        first == 192 && second == 168
    if (!privateAddress) return null
    val port = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else DEVELOPMENT_PORT
    if (port !in 1..65535) return null
    return "http://${parts.first()}:$port$MESSAGE_ROUTE"
}
