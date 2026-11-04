package com.luofelix.laninbox

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
 * Loads an operator-provisioned identity and exposes local HTTPS identity and pairing routes.
 * Keep the connector on loopback until the LAN routes enforce device authentication.
 */
internal fun desktopIdentityServer(
    keyStorePath: Path,
    password: String,
    port: Int = IDENTITY_PORT,
    pairing: DesktopPairingSession = DesktopPairingSession(),
) = run {
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

    embeddedServer(Netty, applicationEnvironment(), configure = {
        requestReadTimeoutSeconds = 10
        sslConnector(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = { password.toCharArray() },
            privateKeyPassword = { password.toCharArray() },
        ) {
            host = "127.0.0.1"
            this.port = port
        }
    }) {
        routing {
            pairingRoutes(pairing)
            get("/v1/identity") {
                call.respondText(fingerprint, ContentType.Text.Plain)
            }
        }
    }
}
