package com.luofelix.laninbox

import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.privateLanAuthority
import io.ktor.http.ContentType
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64

private const val KEY_ALIAS = "laninbox"
private const val IDENTITY_PORT = 8443

/**
 * Loads an operator-provisioned identity and exposes the loopback HTTPS development routes.
 * Keep the connector on loopback while the Android trust flow is under development.
 */
internal fun desktopIdentityServer(
    keyStorePath: Path,
    password: String,
    attachments: DesktopAttachmentStore,
    port: Int = IDENTITY_PORT,
    pairing: DesktopPairingSession = DesktopPairingSession(),
    outbox: DesktopOutbox = DesktopOutbox(),
    bindAddress: String = "127.0.0.1",
    inbox: DesktopInbox? = null,
    logger: AppLogger = AppLogger { },
    onFingerprint: (String) -> Unit = {},
) = run {
    require(bindAddress == "127.0.0.1" ||
        privateLanAuthority(bindAddress, IDENTITY_PORT) == "$bindAddress:$IDENTITY_PORT")
    val keyStore = KeyStore.getInstance("PKCS12")
    Files.newInputStream(keyStorePath).use { keyStore.load(it, password.toCharArray()) }
    require(keyStore.getKey(KEY_ALIAS, password.toCharArray()) is PrivateKey) {
        "The LanInbox identity must contain a private key"
    }
    val certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
        ?: error("The LanInbox identity must contain an X.509 certificate")
    certificate.checkValidity()
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
    val fingerprint = "sha256/${Base64.getEncoder().encodeToString(digest)}"
    onFingerprint(fingerprint)

    embeddedServer(Netty, applicationEnvironment(), configure = {
        requestReadTimeoutSeconds = 10
        sslConnector(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = { password.toCharArray() },
            privateKeyPassword = { password.toCharArray() },
        ) {
            host = bindAddress
            this.port = port
        }
    }) {
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
