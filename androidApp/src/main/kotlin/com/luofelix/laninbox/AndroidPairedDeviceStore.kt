package com.luofelix.laninbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.PairedDevice
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the pairing credential in non-backed-up storage encrypted by Android Keystore. */
internal class AndroidPairedDeviceStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "paired-device.bin"))
    private val alias = "laninbox-pairing"

    @Synchronized
    fun read(): PairedDevice? {
        if (!file.baseFile.exists()) return null
        val encrypted = file.readFully()
        require(encrypted.size in 29..4096)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        return MessageJson.decodeFromString<PairedDevice>(
            cipher.doFinal(encrypted, 12, encrypted.size - 12).decodeToString(),
        )
    }

    @Synchronized
    fun write(device: PairedDevice?) {
        if (device == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(cipher.iv.size == 12)
        val encrypted = cipher.iv + cipher.doFinal(MessageJson.encodeToString(device).encodeToByteArray())
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        if (!file.readFully().contentEquals(encrypted)) throw IOException("Could not commit paired device")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }
}
