package com.luofelix.laninbox

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.luofelix.laninbox.core.network.PairingInvitation
import java.awt.image.BufferedImage

internal fun pairingQr(invitation: PairingInvitation): ImageBitmap {
    val matrix = QRCodeWriter().encode(invitation.encode(), BarcodeFormat.QR_CODE, 320, 320)
    val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) image.setRGB(x, y, if (matrix[x, y]) 0 else 0xFFFFFF)
    }
    return image.toComposeImageBitmap()
}
