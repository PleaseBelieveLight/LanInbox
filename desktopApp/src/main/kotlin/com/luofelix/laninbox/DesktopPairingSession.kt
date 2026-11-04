package com.luofelix.laninbox

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** Holds a single invitation and credential for the lifetime of the loopback receiver. */
internal class DesktopPairingSession(private val clock: TimeSource = TimeSource.Monotonic) {
    private val random = SecureRandom()
    private val mutableInvitation = MutableStateFlow<String?>(null)
    val invitation = mutableInvitation.asStateFlow()
    private var issuedAt = clock.markNow()
    private var credentialDigest: ByteArray? = null

    @Synchronized
    fun issueInvitation() {
        issuedAt = clock.markNow()
        mutableInvitation.value = newSecret()
    }

    /** Returns a fresh credential only once for a valid invitation. */
    @Synchronized
    fun redeem(code: String): String? {
        val active = mutableInvitation.value ?: return null
        if (issuedAt.elapsedNow() >= 2.minutes) {
            mutableInvitation.value = null
            return null
        }
        if (!MessageDigest.isEqual(digest(active), digest(code))) return null

        // Keep consumption and credential replacement atomic across concurrent requests.
        val credential = newSecret()
        credentialDigest = digest(credential)
        mutableInvitation.value = null
        return credential
    }

    @Synchronized
    fun accepts(credential: String): Boolean {
        val expected = credentialDigest ?: return false
        return credential.length == 43 && MessageDigest.isEqual(expected, digest(credential))
    }

    @Synchronized
    fun revoke() {
        mutableInvitation.value = null
        credentialDigest = null
    }

    private fun newSecret(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun digest(secret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(secret.encodeToByteArray())
}
