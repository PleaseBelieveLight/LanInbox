package com.luofelix.laninbox

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
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
    private var lastContact: TimeMark? = null
    private val mutableConnectedSession = MutableStateFlow<Long?>(null)

    /** The pairing with recent authenticated activity, independent of its saved credential. */
    val connectedSession = mutableConnectedSession.asStateFlow()
    private var awaitingConfirmation: Long? = null
    private val mutableConfirmedPairing = MutableStateFlow<Long?>(null)

    /** The newly paired session once the phone makes its first authenticated request. */
    val confirmedPairing = mutableConfirmedPairing.asStateFlow()

    @Synchronized
    fun restore() {
        val saved = store?.read() ?: return
        credentialDigest = saved
        clearConnection()
        awaitingConfirmation = null
        mutableConfirmedPairing.value = null
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
        clearConnection()
        val session = ++generation
        awaitingConfirmation = session
        mutableConfirmedPairing.value = null
        mutableSession.value = session
        mutableInvitation.value = null
        return credential
    }

    @Synchronized
    fun authorizedSession(authorization: String?): Long? {
        val expected = credentialDigest ?: return null
        if (authorization == null || !authorization.startsWith("Bearer ", ignoreCase = true)) return null
        val credential = authorization.substring(7)
        if (credential.length != 43 || !MessageDigest.isEqual(expected, digest(credential))) return null
        val session = mutableSession.value ?: return null
        // Android polls the outbox in the foreground; a saved verifier alone is not presence.
        lastContact = clock.markNow()
        mutableConnectedSession.value = session
        if (awaitingConfirmation == session) {
            // Android saves the credential before making authenticated requests with it.
            awaitingConfirmation = null
            mutableConfirmedPairing.value = session
        }
        return session
    }

    fun acceptsAuthorization(authorization: String?): Boolean = authorizedSession(authorization) != null

    /** Marks an idle phone disconnected without invalidating its pairing or pending deliveries. */
    @Synchronized
    fun expireConnection() {
        if (lastContact?.elapsedNow()?.let { it >= CONNECTION_TIMEOUT } == true) clearConnection()
    }

    @Synchronized
    fun revoke() {
        store?.write(null)
        mutableInvitation.value = null
        credentialDigest = null
        clearConnection()
        awaitingConfirmation = null
        mutableConfirmedPairing.value = null
        mutableSession.value = null
    }

    private fun clearConnection() {
        lastContact = null
        mutableConnectedSession.value = null
    }

    private fun newSecret(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun digest(secret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(secret.encodeToByteArray())

    companion object {
        val INVITATION_LIFETIME = 2.minutes
        val CONNECTION_TIMEOUT = 15.seconds
    }
}
