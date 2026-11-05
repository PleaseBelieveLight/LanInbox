package com.luofelix.laninbox

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** Holds one short-lived invitation and one revocable pairing verifier. */
internal class DesktopPairingSession(
    private val clock: TimeSource = TimeSource.Monotonic,
    private val store: PairingVerifierStore? = null,
) {
    private val random = SecureRandom()
    private val mutableInvitation = MutableStateFlow<String?>(null)
    val invitation = mutableInvitation.asStateFlow()
    private var issuedAt = clock.markNow()
    private var credentialDigest: ByteArray? = null
    private var generation = 0L
    private val mutableSession = MutableStateFlow<Long?>(null)
    val activeSession = mutableSession.asStateFlow()

    @Synchronized
    fun restore() {
        val saved = store?.read() ?: return
        credentialDigest = saved
        mutableSession.value = ++generation
    }

    @Synchronized
    fun issueInvitation() {
        issuedAt = clock.markNow()
        mutableInvitation.value = newSecret()
    }

    @Synchronized
    fun expireInvitation(code: String) {
        if (mutableInvitation.value == code && issuedAt.elapsedNow() >= INVITATION_LIFETIME) {
            mutableInvitation.value = null
        }
    }

    /** Returns a fresh credential only once for a valid invitation. */
    @Synchronized
    fun redeem(code: String): String? {
        val active = mutableInvitation.value ?: return null
        if (issuedAt.elapsedNow() >= INVITATION_LIFETIME) {
            mutableInvitation.value = null
            return null
        }
        if (!MessageDigest.isEqual(digest(active), digest(code))) return null

        // Keep consumption and credential replacement atomic across concurrent requests.
        val credential = newSecret()
        val verifier = digest(credential)
        store?.write(verifier)
        credentialDigest = verifier
        mutableSession.value = ++generation
        mutableInvitation.value = null
        return credential
    }

    @Synchronized
    fun authorizedSession(authorization: String?): Long? {
        val expected = credentialDigest ?: return null
        if (authorization == null || !authorization.startsWith("Bearer ", ignoreCase = true)) return null
        val credential = authorization.substring(7)
        return mutableSession.value.takeIf {
            credential.length == 43 && MessageDigest.isEqual(expected, digest(credential))
        }
    }

    fun acceptsAuthorization(authorization: String?): Boolean = authorizedSession(authorization) != null

    @Synchronized
    fun revoke() {
        store?.write(null)
        mutableInvitation.value = null
        credentialDigest = null
        mutableSession.value = null
    }

    private fun newSecret(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun digest(secret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(secret.encodeToByteArray())

    companion object {
        val INVITATION_LIFETIME = 2.minutes
    }
}
