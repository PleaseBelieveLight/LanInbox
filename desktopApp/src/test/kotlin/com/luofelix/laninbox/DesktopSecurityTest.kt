package com.luofelix.laninbox

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.luofelix.laninbox.core.network.PairingInvitation
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class DesktopSecurityTest {
    @Test
    fun renderedQrCarriesTheVersionedPairingInvitation() {
        val invitation = PairingInvitation("192.168.1.20:8443", "sha256/${"A".repeat(43)}=", "b".repeat(43))
        val image = pairingQr(invitation)
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val source = RGBLuminanceSource(image.width, image.height, pixels)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(invitation.encode(), decoded.text)
        assertNotNull(PairingInvitation.decode(decoded.text))
    }

    @Test
    fun restoresProtectedIdentityAndPairingAcrossWindowsReceiverRestarts() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val directory = Files.createTempDirectory("laninbox-dpapi")
        try {
            val files = DesktopProtectedFiles(directory)
            val initial = provisionDesktopIdentity(files)
            val reopened = provisionDesktopIdentity(DesktopProtectedFiles(directory))
            assertContentEquals(
                initial.keyStore.getCertificate(DESKTOP_KEY_ALIAS).publicKey.encoded,
                reopened.keyStore.getCertificate(DESKTOP_KEY_ALIAS).publicKey.encoded,
            )
            val pairing = DesktopPairingSession(store = WindowsPairingVerifierStore(files))
            pairing.issueInvitation()
            val credential = assertNotNull(pairing.redeem(assertNotNull(pairing.invitation.value)))
            val restored = DesktopPairingSession(store = WindowsPairingVerifierStore(files))
            restored.restore()
            assertTrue(restored.acceptsAuthorization("Bearer $credential"))
            assertNull(restored.confirmedPairing.value)
            restored.revoke()
            val afterRevoke = DesktopPairingSession(store = WindowsPairingVerifierStore(files))
            afterRevoke.restore()
            assertFalse(afterRevoke.acceptsAuthorization("Bearer $credential"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
