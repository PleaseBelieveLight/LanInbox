package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageJson
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class PairingRoutesTest {
    @Test
    fun concurrentRedemptionIssuesOneRevocableCredential() = testApplication {
        val session = DesktopPairingSession()
        application { routing { pairingRoutes(session) } }
        session.issueInvitation()
        val code = assertNotNull(session.invitation.value)

        suspend fun redeem() = client.post("/v1/pairing") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$code"}""")
        }

        val results = coroutineScope { List(3) { async { redeem() } }.awaitAll() }
        assertEquals(2, results.count { it.status == HttpStatusCode.BadRequest })
        val success = results.single { it.status == HttpStatusCode.OK }
        assertEquals("no-store", success.headers[HttpHeaders.CacheControl])
        val credential = MessageJson.parseToJsonElement(success.bodyAsText())
            .jsonObject.getValue("credential").jsonPrimitive.content

        suspend fun verify() = client.get("/v1/pairing/session") {
            header(HttpHeaders.Authorization, "Bearer $credential")
        }.status

        assertNull(session.confirmedPairing.value)
        assertEquals(HttpStatusCode.NoContent, verify())
        assertEquals(assertNotNull(session.activeSession.value), session.confirmedPairing.value)
        session.revoke()
        assertNull(session.confirmedPairing.value)
        assertEquals(HttpStatusCode.Unauthorized, verify())
    }

    @Test
    fun invitationExpiresAtTwoMinutes() {
        val clock = TestTimeSource()
        val session = DesktopPairingSession(clock)
        session.issueInvitation()
        val code = assertNotNull(session.invitation.value)
        clock += 2.minutes
        assertEquals(null, session.redeem(code))
        assertEquals(null, session.invitation.value)
    }

    @Test
    fun keepsOldPairingUntilReplacementInvitationIsRedeemed() {
        val clock = TestTimeSource()
        val session = DesktopPairingSession(clock)
        session.issueInvitation()
        val original = assertNotNull(session.redeem(assertNotNull(session.invitation.value)))
        val originalAuthorization = "Bearer $original"
        val originalSession = assertNotNull(session.authorizedSession(originalAuthorization))

        session.issueInvitation()
        val expired = assertNotNull(session.invitation.value)
        assertEquals(originalSession, session.authorizedSession(originalAuthorization))
        clock += 2.minutes
        session.expireInvitation(expired)
        assertEquals(originalSession, session.authorizedSession(originalAuthorization))

        session.issueInvitation()
        val replacement = assertNotNull(session.redeem(assertNotNull(session.invitation.value)))
        assertEquals(null, session.authorizedSession(originalAuthorization))
        assertNotNull(session.authorizedSession("Bearer $replacement"))
    }

    @Test
    fun invitationExpiryKeepsARefreshedCode() {
        val clock = TestTimeSource()
        val session = DesktopPairingSession(clock)
        session.issueInvitation()
        val oldCode = assertNotNull(session.invitation.value)
        clock += 2.minutes
        session.issueInvitation()
        val refreshed = assertNotNull(session.invitation.value)
        session.expireInvitation(oldCode)
        assertEquals(refreshed, session.invitation.value)
        session.expireInvitation(refreshed)
        assertEquals(refreshed, session.invitation.value)
        clock += 2.minutes
        session.expireInvitation(refreshed)
        assertEquals(null, session.invitation.value)
    }
}
