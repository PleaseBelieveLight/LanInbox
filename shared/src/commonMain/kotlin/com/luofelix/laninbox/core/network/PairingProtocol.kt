package com.luofelix.laninbox.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

const val LAN_SERVICE_TYPE = "_laninbox._tcp."
const val SECURE_PORT = 8443
private const val INVITATION_PREFIX = "laninbox:v1:"
private val fingerprintPattern = Regex("sha256/[A-Za-z0-9+/]{43}=")
private val credentialPattern = Regex("[A-Za-z0-9_-]{43}")

fun isFingerprint(value: String): Boolean = fingerprintPattern.matches(value)
fun isPairingSecret(value: String): Boolean = credentialPattern.matches(value)

@Serializable
class PairingInvitation(val address: String, val fingerprint: String, val code: String) {
    fun encode(): String = INVITATION_PREFIX + MessageJson.encodeToString(this)

    companion object {
        fun decode(payload: String): PairingInvitation? {
            if (payload.length > 512 || !payload.startsWith(INVITATION_PREFIX)) return null
            val result = try {
                MessageJson.decodeFromString<PairingInvitation>(payload.removePrefix(INVITATION_PREFIX))
            } catch (_: SerializationException) {
                return null
            }
            return result.takeIf {
                privateLanAuthority(it.address, SECURE_PORT) != null &&
                    isFingerprint(it.fingerprint) && isPairingSecret(it.code)
            }
        }
    }
}

/** A credential and the pinned identity it is permitted to contact. */
@Serializable
class PairedDevice(val address: String, val fingerprint: String, val credential: String)
