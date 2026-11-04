package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.ProtocolError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun Route.pairingRoutes(session: DesktopPairingSession) {
    post("/v1/pairing") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val code = if (call.request.contentType().match(ContentType.Application.Json)) {
            val bytes = call.receiveChannel().readRemaining(513).readByteArray()
            if (bytes.size <= 512) invitationCode(bytes.decodeToString()) else null
        } else {
            null
        }
        val credential = code?.let(session::redeem)
        if (credential == null) {
            call.respondText(
                MessageJson.encodeToString(ProtocolError("invalid_pairing_invitation")),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
        } else {
            val body = buildJsonObject { put("credential", credential) }
            call.respondText(body.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }
    }

    get("/v1/pairing/session") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val authorization = call.request.headers[HttpHeaders.Authorization].orEmpty()
        val credential = authorization.substringAfter(' ', "")
        if (authorization.substringBefore(' ').equals("Bearer", ignoreCase = true) &&
            session.accepts(credential)
        ) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}

private fun invitationCode(body: String): String? {
    val json = try {
        MessageJson.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
    val code = json?.get("code") as? JsonPrimitive ?: return null
    return code.content.takeIf { code.isString && it.length == 43 }
}
