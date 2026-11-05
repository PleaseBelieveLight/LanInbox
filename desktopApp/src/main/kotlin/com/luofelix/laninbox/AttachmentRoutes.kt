package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.ProtocolError
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.ATTACHMENT_HASH_HEADER
import com.luofelix.laninbox.core.network.ATTACHMENT_NAME_HEADER
import com.luofelix.laninbox.core.network.ATTACHMENT_SIZE_HEADER
import com.luofelix.laninbox.core.network.ATTACHMENT_TYPE_HEADER
import com.luofelix.laninbox.core.network.MAX_ATTACHMENT_BYTES
import com.luofelix.laninbox.core.network.attachmentMediaType
import com.luofelix.laninbox.core.network.safeAttachmentName
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes

internal fun Route.attachmentRoutes(pairing: DesktopPairingSession, store: DesktopAttachmentStore) {
    put("/v1/attachments/{id}") {
        if (call.requireSession(pairing) == null) return@put

        val size = call.request.headers[ATTACHMENT_SIZE_HEADER]?.toLongOrNull()
        val request = AttachmentInfo(
            id = call.parameters["id"].orEmpty(),
            size = size ?: -1,
            sha256 = call.request.headers[ATTACHMENT_HASH_HEADER].orEmpty(),
            name = decodeName(call.request.headers[ATTACHMENT_NAME_HEADER]),
            mediaType = attachmentMediaType(call.request.headers[ATTACHMENT_TYPE_HEADER]),
        )
        val failure = when {
            !call.request.contentType().match(ContentType.Application.OctetStream) ->
                HttpStatusCode.UnsupportedMediaType to "unsupported_content_type"
            size != null && size > MAX_ATTACHMENT_BYTES ->
                HttpStatusCode.PayloadTooLarge to "attachment_too_large"
            !request.isValid() -> HttpStatusCode.BadRequest to "invalid_attachment_metadata"
            else -> null
        }
        if (failure != null) {
            val (status, code) = failure
            call.respondText(MessageJson.encodeToString(ProtocolError(code)), ContentType.Application.Json, status)
            return@put
        }
        val result = try {
            withTimeoutOrNull(5.minutes) { store.save(request, call.receiveChannel()) }
        } catch (_: IOException) {
            call.respondText(
                MessageJson.encodeToString(ProtocolError("attachment_io_failure")),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
            return@put
        }
        val (status, error) = when (result) {
            AttachmentResult.Created -> HttpStatusCode.Created to null
            AttachmentResult.Replay -> HttpStatusCode.OK to null
            AttachmentResult.Conflict -> HttpStatusCode.Conflict to "attachment_id_conflict"
            AttachmentResult.InvalidBody -> HttpStatusCode.BadRequest to "attachment_integrity_failure"
            AttachmentResult.TooLarge -> HttpStatusCode.PayloadTooLarge to "attachment_too_large"
            AttachmentResult.Busy -> HttpStatusCode.ServiceUnavailable to "attachment_receiver_busy"
            null -> HttpStatusCode.RequestTimeout to "attachment_timeout"
        }
        val body = if (error == null) {
            buildJsonObject {
                put("id", request.id)
                put("size", request.size)
                put("sha256", request.sha256)
            }.toString()
        } else {
            MessageJson.encodeToString(ProtocolError(error))
        }
        call.respondText(body, ContentType.Application.Json, status)
    }
}

private fun decodeName(value: String?): String {
    if (value == null || value.length > 640) return "attachment"
    return try {
        safeAttachmentName(Base64.getUrlDecoder().decode(value).decodeToString())
    } catch (_: IllegalArgumentException) {
        "attachment"
    }
}
