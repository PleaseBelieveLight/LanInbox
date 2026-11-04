package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.ATTACHMENT_HASH_HEADER
import com.luofelix.laninbox.core.network.ATTACHMENT_NAME_HEADER
import com.luofelix.laninbox.core.network.ATTACHMENT_SIZE_HEADER
import com.luofelix.laninbox.core.network.ATTACHMENT_TYPE_HEADER
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MAX_REQUEST_BYTES
import com.luofelix.laninbox.core.network.MESSAGE_ROUTE
import com.luofelix.laninbox.core.network.MessageAcknowledgment
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.core.network.OutboundDelivery
import com.luofelix.laninbox.core.network.privateLanAuthority
import com.luofelix.laninbox.feature.send.DeliveryResult
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val json = "application/json".toMediaType()
private val binary = "application/octet-stream".toMediaType()

class SessionExpired : IOException("Pairing session expired")

class SecureLanClient private constructor(
    val address: String,
    private val http: OkHttpClient,
    private val credential: String,
) {
    suspend fun sendMessage(message: MessageRequest): DeliveryResult = try {
        http.exchange(request(MESSAGE_ROUTE).post(MessageJson.encodeToString(message).toRequestBody(json))) { response ->
            if (!response.isSuccessful) DeliveryResult.Rejected
            else if (MessageJson.decodeFromString<MessageAcknowledgment>(response.readJson()).messageId == message.messageId) {
                DeliveryResult.Delivered
            } else DeliveryResult.InvalidResponse
        }
    } catch (_: java.net.SocketTimeoutException) {
        DeliveryResult.Timeout
    } catch (_: IOException) {
        DeliveryResult.Unreachable
    } catch (_: kotlinx.serialization.SerializationException) {
        DeliveryResult.InvalidResponse
    }

    suspend fun upload(info: AttachmentInfo, file: File, progress: (Long) -> Unit) {
        val body = object : RequestBody() {
            override fun contentType() = binary
            override fun contentLength() = info.size
            override fun writeTo(sink: BufferedSink) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        sent += count
                        progress(sent)
                    }
                }
            }
        }
        http.exchange(
            request("/v1/attachments/${info.id}")
                .header(ATTACHMENT_SIZE_HEADER, info.size.toString())
                .header(ATTACHMENT_HASH_HEADER, info.sha256)
                .header(ATTACHMENT_NAME_HEADER, info.name.encodeToByteArray().toByteString().base64Url())
                .header(ATTACHMENT_TYPE_HEADER, info.mediaType)
                .put(body),
        ) { response ->
            response.requireSuccess()
            val receipt = MessageJson.decodeFromString<AttachmentInfo>(response.readJson())
            if (receipt.id != info.id || receipt.size != info.size || receipt.sha256 != info.sha256) {
                throw IOException("Invalid attachment receipt")
            }
        }
    }

    suspend fun next(): OutboundDelivery? = http.exchange(request("/v1/outbox")) { response ->
        response.requireSuccess()
        if (response.code == 204) null else {
            MessageJson.decodeFromString<OutboundDelivery>(response.readJson()).takeIf { it.isValid() }
                ?: throw IOException("Invalid pending delivery")
        }
    }

    suspend fun download(info: AttachmentInfo, destination: File, progress: (Long) -> Unit) {
        http.exchange(request("/v1/outbox/${info.id}/payload")) { response ->
            response.requireSuccess()
            val body = response.body ?: throw IOException("Missing attachment payload")
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > info.size) throw IOException("Attachment exceeded declared size")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        progress(received)
                    }
                }
            }
            if (received != info.size || digest.digest().toByteString().hex() != info.sha256) {
                throw IOException("Attachment integrity check failed")
            }
        }
    }

    suspend fun acknowledge(id: String) {
        http.exchange(request("/v1/outbox/$id/ack").post(ByteArray(0).toRequestBody())) { response ->
            // A retry after a lost response can arrive after the server already removed the item.
            if (response.code != 404) response.requireSuccess()
        }
    }

    fun close() {
        http.dispatcher.cancelAll()
        http.connectionPool.evictAll()
    }

    private fun request(path: String): Request.Builder =
        Request.Builder().url("https://$address$path").header("Authorization", "Bearer $credential")

    companion object {
        suspend fun pair(address: String, fingerprint: String, invitation: String): SecureLanClient {
            val endpoint = privateLanAuthority(address, 8443) ?: loopbackAuthority(address)
                ?: throw IOException("Invalid local IPv4 address")
            val pin = fingerprint.trim()
            if (!pin.matches(Regex("sha256/[A-Za-z0-9+/]{43}="))) throw IOException("Invalid fingerprint")
            val expected = pin.substringAfter('/').decodeBase64()?.toByteArray()
                ?: throw IOException("Invalid fingerprint")
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    throw CertificateException("Client certificates are unsupported")
                }
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val leaf = chain.firstOrNull() ?: throw CertificateException("Missing server certificate")
                    leaf.checkValidity()
                    val actual = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
                    if (!MessageDigest.isEqual(expected, actual)) throw CertificateException("Unexpected server key")
                }
            }
            // The pin is the trust anchor. OkHttp still checks the address against the certificate SAN.
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            val client = OkHttpClient.Builder()
                .sslSocketFactory(tls.socketFactory, trust)
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.MINUTES)
                .build()
            val payload = buildJsonObject { put("code", invitation.trim()) }.toString()
            val credential = client.exchange(
                Request.Builder().url("https://$endpoint/v1/pairing").post(payload.toRequestBody(json)),
            ) { response ->
                response.requireSuccess()
                MessageJson.parseToJsonElement(response.readJson()).jsonObject["credential"]
                    ?.jsonPrimitive?.content?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
                    ?: throw IOException("Invalid pairing response")
            }
            return SecureLanClient(endpoint, client, credential)
        }
    }
}

private fun loopbackAuthority(address: String): String? {
    val parts = address.trim().split(':')
    if (parts.size > 2 || parts.first() != "127.0.0.1") return null
    val port = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 8443
    return "127.0.0.1:$port".takeIf { port in 1..65535 }
}

private suspend fun <T> OkHttpClient.exchange(builder: Request.Builder, consume: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(builder.build())
        // Keep the callback active through streaming so cancellation also closes the response socket.
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use(consume) }
                continuation.resumeWith(result)
            }
        })
    }

private fun Response.requireSuccess() {
    if (code == 401) throw SessionExpired()
    if (!isSuccessful) throw IOException("Receiver rejected the request")
}

private fun Response.readJson(): String {
    val source = body?.source() ?: throw IOException("Missing response")
    if (source.request(MAX_REQUEST_BYTES.toLong() + 1)) throw IOException("Response is too large")
    return source.readUtf8()
}
