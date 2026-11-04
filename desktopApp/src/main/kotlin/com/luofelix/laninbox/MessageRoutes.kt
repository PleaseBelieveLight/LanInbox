package com.luofelix.laninbox

import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.MAX_REQUEST_BYTES
import com.luofelix.laninbox.core.network.MESSAGE_ROUTE
import com.luofelix.laninbox.core.network.MessageAcknowledgment
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.core.network.ProtocolError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import java.io.IOException
import java.sql.SQLException
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException

internal fun Application.messageRoutes(
    inbox: DesktopInbox,
    logger: AppLogger,
    pairing: DesktopPairingSession? = null,
) {
    routing {
        post(MESSAGE_ROUTE) {
            if (pairing != null && call.requireSession(pairing) == null) return@post
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                call.respondText(
                    MessageJson.encodeToString(ProtocolError("unsupported_content_type")),
                    ContentType.Application.Json,
                    HttpStatusCode.UnsupportedMediaType,
                )
                return@post
            }
            // Read one extra byte to detect an oversized body without buffering the entire request.
            val bytes = call.receiveChannel().readRemaining(MAX_REQUEST_BYTES.toLong() + 1)
                .readByteArray()
            if (bytes.size > MAX_REQUEST_BYTES) {
                call.respondText(
                    MessageJson.encodeToString(ProtocolError("request_too_large")),
                    ContentType.Application.Json,
                    HttpStatusCode.PayloadTooLarge,
                )
                return@post
            }
            val message = try {
                MessageJson.decodeFromString<MessageRequest>(bytes.decodeToString(throwOnInvalidSequence = true))
            } catch (_: SerializationException) {
                null
            } catch (_: java.nio.charset.CharacterCodingException) {
                null
            }
            if (message == null || !message.isValid()) {
                call.respondText(
                    MessageJson.encodeToString(ProtocolError("invalid_message")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@post
            }
            val saved = try {
                inbox.save(message)
            } catch (_: SQLException) {
                null
            } catch (_: IOException) {
                null
            }
            // Acknowledge only after persistence so a failed write cannot appear as delivered.
            val (status, body) = when (saved) {
                true -> HttpStatusCode.OK to MessageJson.encodeToString(MessageAcknowledgment(message.messageId))
                false -> HttpStatusCode.Conflict to MessageJson.encodeToString(ProtocolError("message_id_conflict"))
                null -> HttpStatusCode.InternalServerError to MessageJson.encodeToString(ProtocolError("storage_failure"))
            }
            logger.info("Message request handled status=${status.value}")
            call.respondText(body, ContentType.Application.Json, status)
        }
    }
}
