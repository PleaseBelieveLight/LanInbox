package com.luofelix.laninbox

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyStore
import javax.security.auth.x500.X500Principal

/** Uses the Windows account's DPAPI key to protect application-owned security material. */
internal class DesktopProtectedFiles(private val directory: Path) {
    fun read(name: String): ByteArray? {
        val path = directory.resolve(name)
        if (Files.notExists(path)) return null
        return Crypt32Util.cryptUnprotectData(Files.readAllBytes(path), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
    }

    fun write(name: String, data: ByteArray?) {
        val path = directory.resolve(name)
        if (data == null) {
            Files.deleteIfExists(path)
            return
        }
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".protected-", ".part")
        try {
            Files.write(temporary, Crypt32Util.cryptProtectData(data, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN))
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal class DesktopTlsIdentity(val keyStore: KeyStore, val password: String)

internal fun loadDesktopIdentity(path: Path, password: String): DesktopTlsIdentity {
    val store = KeyStore.getInstance("PKCS12")
    Files.newInputStream(path).use { store.load(it, password.toCharArray()) }
    return DesktopTlsIdentity(store, password)
}

internal fun provisionDesktopIdentity(files: DesktopProtectedFiles): DesktopTlsIdentity {
    val password = ""
    val saved = files.read("identity.dpapi")
    if (saved != null) {
        val store = KeyStore.getInstance("PKCS12")
        saved.inputStream().use { store.load(it, password.toCharArray()) }
        return DesktopTlsIdentity(store, password)
    }
    val store = buildKeyStore {
        certificate(DESKTOP_KEY_ALIAS) {
            this.password = password
            hash = HashAlgorithm.SHA256
            keySizeInBits = 3072
            daysValid = 3650
            subject = X500Principal("CN=LanInbox")
        }
    }
    val encoded = ByteArrayOutputStream().also { store.store(it, password.toCharArray()) }.toByteArray()
    try {
        files.write("identity.dpapi", encoded)
    } finally {
        encoded.fill(0)
    }
    return DesktopTlsIdentity(store, password)
}

internal interface PairingVerifierStore {
    fun read(): ByteArray?
    fun write(digest: ByteArray?)
}

internal class WindowsPairingVerifierStore(private val files: DesktopProtectedFiles) : PairingVerifierStore {
    override fun read(): ByteArray? = files.read("pairing.dpapi")?.also {
        if (it.size != 32) throw IOException("Invalid stored pairing verifier")
    }

    override fun write(digest: ByteArray?) = files.write("pairing.dpapi", digest)
}
