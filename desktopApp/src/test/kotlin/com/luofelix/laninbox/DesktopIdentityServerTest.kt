package com.luofelix.laninbox

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
import kotlinx.coroutines.runBlocking

class DesktopIdentityServerTest {
    @Test
    fun exposesOnlyTheIdentityProbeOverTrustedLoopbackTls() {
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
            val server = desktopIdentityServer(file, password, port = 0)
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
