package com.luofelix.laninbox

import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.privateLanAuthority
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64

internal const val DESKTOP_KEY_ALIAS = "laninbox"
private const val IDENTITY_PORT = 8443

/**
 * Serves the pinned desktop identity and authenticated transfer routes over HTTPS.
 */
internal fun desktopIdentityServer(
    identity: DesktopTlsIdentity,
    attachments: DesktopAttachmentStore,
    port: Int = IDENTITY_PORT,
    pairing: DesktopPairingSession = DesktopPairingSession(),
    outbox: DesktopOutbox = DesktopOutbox(),
    bindAddress: String = "127.0.0.1",
    inbox: DesktopInbox? = null,
    logger: AppLogger = AppLogger { },
    onFingerprint: (String) -> Unit = {},
) = run {
    require(bindAddress == "127.0.0.1" || bindAddress == "0.0.0.0" ||
        privateLanAuthority(bindAddress, IDENTITY_PORT) == "$bindAddress:$IDENTITY_PORT")
    val keyStore = identity.keyStore
    val password = identity.password
    require(keyStore.getKey(DESKTOP_KEY_ALIAS, password.toCharArray()) is PrivateKey) {
        "The LanInbox identity must contain a private key"
    }
    val certificate = keyStore.getCertificate(DESKTOP_KEY_ALIAS) as? X509Certificate
        ?: error("The LanInbox identity must contain an X.509 certificate")
    certificate.checkValidity()
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
    val fingerprint = "sha256/${Base64.getEncoder().encodeToString(digest)}"
    onFingerprint(fingerprint)

    embeddedServer(Netty, applicationEnvironment(), configure = {
        requestReadTimeoutSeconds = 10
        sslConnector(
            keyStore = keyStore,
            keyAlias = DESKTOP_KEY_ALIAS,
            keyStorePassword = { password.toCharArray() },
            privateKeyPassword = { password.toCharArray() },
        ) {
            host = bindAddress
            this.port = port
        }
    }) {
        intercept(ApplicationCallPipeline.Plugins) {
            val peer = call.request.local.remoteAddress
            if (peer != "127.0.0.1" && privateLanAuthority(peer, IDENTITY_PORT) == null) {
                call.respond(HttpStatusCode.Forbidden)
                finish()
            }
        }
        if (inbox != null) messageRoutes(inbox, logger, pairing)
        routing {
            pairingRoutes(pairing)
            attachmentRoutes(pairing, attachments)
            outboxRoutes(pairing, outbox)
            get("/v1/identity") {
                call.respondText(fingerprint, ContentType.Text.Plain)
            }
        }
    }
}
