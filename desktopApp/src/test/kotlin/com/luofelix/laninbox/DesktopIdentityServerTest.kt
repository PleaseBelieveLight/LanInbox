package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.core.network.MAX_ATTACHMENT_BYTES
import com.luofelix.laninbox.core.network.OutboundDelivery
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import java.awt.image.BufferedImage
import java.net.URI
import java.io.IOException
import java.io.OutputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.security.DigestOutputStream
import java.time.Duration
import java.util.Base64
import java.util.HexFormat
import javax.imageio.ImageIO
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DesktopIdentityServerTest {
    @Test
    fun exchangesAttachmentsAndTextOverTrustedLoopbackTls() {
        val directory = Files.createTempDirectory("laninbox-identity")
        val file = directory.resolve("identity.p12")
        val attachmentDirectory = directory.resolve("attachments")
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
            val outbox = DesktopOutbox()
            val attachments = DesktopAttachmentStore(attachmentDirectory)
            val inbox = DesktopInbox(directory.resolve("inbox.sqlite"))
            runBlocking { inbox.initialize() }
            val server = desktopIdentityServer(
                loadDesktopIdentity(file, password),
                attachments,
                port = 0,
                pairing = pairing,
                outbox = outbox,
                inbox = inbox,
            )
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

                val id = "00000000-0000-4000-8000-000000000001"
                val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
                val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                val upload = HttpRequest.newBuilder(URI.create("$baseUrl/v1/attachments/$id"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer $credential")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-LanInbox-Size", bytes.size.toString())
                    .header("X-LanInbox-SHA256", digest)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build()
                assertEquals(201, client.send(upload, HttpResponse.BodyHandlers.discarding()).statusCode())
                assertEquals(200, client.send(upload, HttpResponse.BodyHandlers.discarding()).statusCode())
                assertContentEquals(bytes, Files.readAllBytes(attachmentDirectory.resolve(id).resolve("payload")))

                fun authenticated(path: String) = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer $credential")

                fun nextDelivery(): OutboundDelivery {
                    val next = client.send(authenticated("/v1/outbox").build(), HttpResponse.BodyHandlers.ofString())
                    assertEquals(200, next.statusCode())
                    return MessageJson.decodeFromString(next.body())
                }

                fun acknowledge(delivery: OutboundDelivery) {
                    val ack = authenticated("/v1/outbox/${delivery.id}/ack")
                        .POST(HttpRequest.BodyPublishers.noBody()).build()
                    assertEquals(204, client.send(ack, HttpResponse.BodyHandlers.discarding()).statusCode())
                }

                val owner = assertNotNull(pairing.activeSession.value)
                assertTrue(outbox.enqueueText(owner, "中文\n    code", MessageKind.Code))
                val text = nextDelivery()
                assertEquals("中文\n    code", text.message?.content)
                assertEquals(text, nextDelivery())
                acknowledge(text)

                val outgoing = directory.resolve("photo.png")
                assertTrue(ImageIO.write(BufferedImage(17, 9, BufferedImage.TYPE_INT_RGB), "png", outgoing.toFile()))
                val imageBytes = Files.readAllBytes(outgoing)
                val imageDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(imageBytes))
                assertTrue(runBlocking { outbox.enqueueFile(owner, outgoing) })
                val offered = nextDelivery()
                val attachment = assertNotNull(offered.attachment)
                assertEquals("photo.png", attachment.name)
                assertEquals(imageDigest, attachment.sha256)
                assertNull(outbox.next(owner + 1))
                val download = authenticated("/v1/outbox/${offered.id}/payload").build()
                repeat(2) {
                    val downloaded = client.send(download, HttpResponse.BodyHandlers.ofByteArray())
                    assertEquals(200, downloaded.statusCode())
                    assertContentEquals(imageBytes, downloaded.body())
                }
                acknowledge(offered)
                assertEquals(204, client.send(authenticated("/v1/outbox").build(), HttpResponse.BodyHandlers.discarding()).statusCode())

                val incomingMessage = checkNotNull(text.message).copy(senderId = "android")
                val submit = authenticated("/v1/messages")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MessageJson.encodeToString(incomingMessage)))
                    .build()
                assertEquals(200, client.send(submit, HttpResponse.BodyHandlers.discarding()).statusCode())
                assertEquals(incomingMessage, inbox.messages.value.single().request)

                pairing.revoke()
                assertEquals(401, client.send(sessionRequest, HttpResponse.BodyHandlers.discarding()).statusCode())
                assertEquals(401, client.send(upload, HttpResponse.BodyHandlers.discarding()).statusCode())

                val messageRequest = HttpRequest.newBuilder(URI.create("$baseUrl/v1/messages"))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build()
                assertEquals(401, client.send(messageRequest, HttpResponse.BodyHandlers.discarding()).statusCode())

                // Exercise the same pinning and streaming client compiled into the Android app.
                runBlocking {
                    pairing.issueInvitation()
                    val sharedCode = assertNotNull(pairing.invitation.value)
                    val address = "127.0.0.1:${connector.port}"
                    assertFailsWith<IOException> {
                        SecureLanClient.pair(address, "sha256/${Base64.getEncoder().encodeToString(ByteArray(32))}", sharedCode)
                    }
                    val androidClient = SecureLanClient.pair(address, response.body(), sharedCode)
                    try {
                        assertNull(androidClient.next())
                        val sharedOwner = assertNotNull(pairing.activeSession.value)
                        assertTrue(outbox.enqueueFile(sharedOwner, outgoing))
                        val sharedDelivery = assertNotNull(androidClient.next())
                        val sharedAttachment = assertNotNull(sharedDelivery.attachment)
                        val phoneFile = directory.resolve("phone-download")
                        assertFailsWith<IOException> {
                            androidClient.download(sharedAttachment, phoneFile.toFile()) {
                                throw IOException("Simulated interruption")
                            }
                        }
                        assertEquals(sharedDelivery, androidClient.next())
                        androidClient.download(sharedAttachment, phoneFile.toFile()) {}
                        assertContentEquals(imageBytes, Files.readAllBytes(phoneFile))
                        androidClient.acknowledge(sharedDelivery.id)
                        assertNull(androidClient.next())
                        androidClient.upload(sharedAttachment, phoneFile.toFile()) {}
                        assertEquals("photo.png", attachments.attachments.value.first().name)
                        val stored = attachments.payload(sharedAttachment)
                        assertContentEquals(imageBytes, Files.readAllBytes(stored))
                        val preview = assertNotNull(desktopPreview(stored))
                        assertEquals(17, preview.width)
                        assertEquals(9, preview.height)
                        assertEquals(
                            com.luofelix.laninbox.feature.send.DeliveryResult.Delivered,
                            androidClient.sendMessage(incomingMessage),
                        )
                        val large = directory.resolve("large.bin")
                        val block = ByteArray(64 * 1024) { (it % 251).toByte() }
                        Files.newOutputStream(large).use { output ->
                            repeat((MAX_ATTACHMENT_BYTES / block.size).toInt() - 1) { output.write(block) }
                            output.write(block, 0, block.size - 1)
                        }
                        assertTrue(outbox.enqueueFile(sharedOwner, large))
                        val largeOffer = assertNotNull(androidClient.next())
                        val largeAttachment = assertNotNull(largeOffer.attachment)
                        assertEquals(MAX_ATTACHMENT_BYTES - 1, largeAttachment.size)
                        val largeRequest = HttpRequest.newBuilder(URI.create("$baseUrl/v1/outbox/${largeOffer.id}/payload"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer ${androidClient.device.credential}")
                            .build()
                        val streaming = client.send(largeRequest, HttpResponse.BodyHandlers.ofInputStream())
                        assertEquals(200, streaming.statusCode())
                        streaming.body().use { input ->
                            assertEquals(
                                503,
                                client.send(largeRequest, HttpResponse.BodyHandlers.discarding()).statusCode(),
                            )
                            val digestStream = MessageDigest.getInstance("SHA-256")
                            DigestOutputStream(OutputStream.nullOutputStream(), digestStream).use { output ->
                                assertEquals(largeAttachment.size, input.transferTo(output))
                            }
                            assertEquals(largeAttachment.sha256, HexFormat.of().formatHex(digestStream.digest()))
                        }
                        androidClient.acknowledge(largeOffer.id)
                        androidClient.upload(largeAttachment, large.toFile()) {}
                        assertEquals(largeAttachment.size, Files.size(attachments.payload(largeAttachment)))
                        pairing.revoke()
                        assertFailsWith<SessionExpired> { androidClient.next() }
                    } finally {
                        androidClient.close()
                    }
                }
            } finally {
                outbox.cancel()
                server.stop(0, 1_500)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
