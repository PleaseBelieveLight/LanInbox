package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageJson
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DesktopIdentityServerTest {
    @Test
    fun pairsOverTrustedLoopbackTlsWithoutExposingMessageSubmission() {
        val file = Files.createTempFile("laninbox-identity", ".p12")
        val password = "test-password"
        try {
            val keyStore = buildKeyStore {
                certificate("laninbox") {
                    this.password = password
                    hash = HashAlgorithm.SHA256
                    keySizeInBits = 2048
                }
            }
            Files.newOutputStream(file).use { keyStore.store(it, password.toCharArray()) }
            val pairing = DesktopPairingSession()
            val server = desktopIdentityServer(file, password, port = 0, pairing = pairing)
            try {
                server.start(wait = false)
                val connector = runBlocking { server.engine.resolvedConnectors().single() }
                assertEquals("127.0.0.1", connector.host)
                val baseUrl = "https://127.0.0.1:${connector.port}"
                val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/identity"))
                    .timeout(Duration.ofSeconds(5))
                    .build()
                assertFailsWith<SSLHandshakeException> {
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
                }

                val trust = KeyStore.getInstance("PKCS12").apply {
                    load(null, null)
                    setCertificateEntry("laninbox", keyStore.getCertificate("laninbox"))
                }
                val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply { init(trust) }
                val context = SSLContext.getInstance("TLS").apply {
                    init(null, trustManagers.trustManagers, null)
                }
                val client = HttpClient.newBuilder().sslContext(context).build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val peerKey = response.sslSession().orElseThrow().peerCertificates.first().publicKey
                val hash = MessageDigest.getInstance("SHA-256").digest(peerKey.encoded)
                assertEquals(200, response.statusCode())
                assertEquals("sha256/${Base64.getEncoder().encodeToString(hash)}", response.body())

                pairing.issueInvitation()
                val code = assertNotNull(pairing.invitation.value)
                val redemption = HttpRequest.newBuilder(URI.create("$baseUrl/v1/pairing"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"code":"$code"}"""))
                    .build()
                val paired = client.send(redemption, HttpResponse.BodyHandlers.ofString())
                assertEquals(200, paired.statusCode())
                assertEquals(400, client.send(redemption, HttpResponse.BodyHandlers.discarding()).statusCode())
                val credential = MessageJson.parseToJsonElement(paired.body())
                    .jsonObject.getValue("credential").jsonPrimitive.content
                val sessionRequest = HttpRequest.newBuilder(URI.create("$baseUrl/v1/pairing/session"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer $credential")
                    .build()
                assertEquals(204, client.send(sessionRequest, HttpResponse.BodyHandlers.discarding()).statusCode())
                pairing.revoke()
                assertEquals(401, client.send(sessionRequest, HttpResponse.BodyHandlers.discarding()).statusCode())

                val messageRequest = HttpRequest.newBuilder(URI.create("$baseUrl/v1/messages"))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build()
                assertEquals(404, client.send(messageRequest, HttpResponse.BodyHandlers.discarding()).statusCode())
            } finally {
                server.stop(0, 1_500)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
