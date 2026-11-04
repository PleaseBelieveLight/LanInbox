package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.PairingInvitation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedCommonTest {

    @Test
    fun acceptsOnlyVersionedInvitationsWithPrivateAddressesAndValidSecrets() {
        val pin = "sha256/${"A".repeat(43)}="
        val code = "a".repeat(43)
        val invitation = PairingInvitation("192.168.1.8:8443", pin, code)
        val decoded = assertNotNull(PairingInvitation.decode(invitation.encode()))
        assertEquals(invitation.address, decoded.address)
        assertEquals(pin, decoded.fingerprint)
        assertEquals(code, decoded.code)
        assertNull(PairingInvitation.decode(invitation.encode().replace(":v1:", ":v2:")))
        assertNull(PairingInvitation.decode(PairingInvitation("8.8.8.8:8443", pin, code).encode()))
        assertNull(PairingInvitation.decode(PairingInvitation(invitation.address, pin, "short").encode()))
    }
}
