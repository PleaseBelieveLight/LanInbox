package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.PairingInvitation
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.feature.send.draftKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedCommonTest {

    @Test
    fun recognizesStandaloneWebLinksAndHonorsCodeFormat() {
        assertEquals(MessageKind.Link, draftKind(" https://example.com/旅程 ", asCode = false))
        assertEquals(MessageKind.Text, draftKind("链接 https://example.com", asCode = false))
        assertEquals(MessageKind.Text, draftKind("https://example.com\nhttps://example.org", asCode = false))
        assertEquals(MessageKind.Code, draftKind("https://example.com", asCode = true))
    }

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
