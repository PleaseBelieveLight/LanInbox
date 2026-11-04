package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.sync.Semaphore

internal fun Route.outboxRoutes(pairing: DesktopPairingSession, outbox: DesktopOutbox) {
    val downloads = Semaphore(1)
    get("/v1/outbox") {
        val session = call.requireSession(pairing) ?: return@get
        val delivery = outbox.next(session)?.delivery
        if (delivery == null) call.respond(HttpStatusCode.NoContent)
        else call.respondText(MessageJson.encodeToString(delivery), ContentType.Application.Json)
    }
    get("/v1/outbox/{id}/payload") {
        val session = call.requireSession(pairing) ?: return@get
        val entry = outbox.next(session)?.takeIf { it.delivery.attachment?.id == call.parameters["id"] }
        if (entry == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val size = checkNotNull(entry.delivery.attachment).size
        val authorization = call.request.headers[HttpHeaders.Authorization]
        if (!downloads.tryAcquire()) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@get
        }
        try {
            call.respondOutputStream(ContentType.Application.OctetStream, contentLength = size) {
                outbox.writeFile(entry, this) { pairing.authorizedSession(authorization) == session }
            }
        } finally {
            downloads.release()
        }
    }
    post("/v1/outbox/{id}/ack") {
        val session = call.requireSession(pairing) ?: return@post
        if (outbox.acknowledge(session, call.parameters["id"].orEmpty())) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

internal suspend fun ApplicationCall.requireSession(pairing: DesktopPairingSession): Long? {
    response.header(HttpHeaders.CacheControl, "no-store")
    val session = pairing.authorizedSession(request.headers[HttpHeaders.Authorization])
    if (session == null) {
        response.header(HttpHeaders.WWWAuthenticate, "Bearer")
        respond(HttpStatusCode.Unauthorized)
    }
    return session
}
