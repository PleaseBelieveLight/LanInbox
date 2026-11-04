package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MAX_ATTACHMENT_BYTES
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Files
import java.io.FilterOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DesktopAttachmentStoreTest {
    @Test
    fun rejectsInvalidBodiesAndPreservesTheFirstVerifiedPayload() = runBlocking {
        val root = Files.createTempDirectory("laninbox-attachments")
        try {
            val store = DesktopAttachmentStore(root)
            val bytes = byteArrayOf(1, 2, 3)
            val replacement = byteArrayOf(4, 5, 6)
            val first = request(bytes)
            assertFalse(first.copy(id = "../escape").isValid())
            assertFalse(first.copy(size = MAX_ATTACHMENT_BYTES + 1).isValid())
            assertEquals(AttachmentResult.InvalidBody, store.save(first, ByteReadChannel(replacement)))
            assertEquals(AttachmentResult.InvalidBody, store.save(first, ByteReadChannel(byteArrayOf(1))))
            assertEquals(AttachmentResult.InvalidBody, store.save(first, ByteReadChannel(bytes + 4.toByte())))
            assertEquals(0L, Files.list(root).use { it.count() })

            assertEquals(AttachmentResult.Created, store.save(first, ByteReadChannel(bytes)))
            val reopened = DesktopAttachmentStore(root)
            assertEquals(AttachmentResult.Conflict, reopened.save(request(replacement), ByteReadChannel(replacement)))
            assertEquals(AttachmentResult.Replay, reopened.save(first, ByteReadChannel(bytes)))
            assertContentEquals(bytes, Files.readAllBytes(root.resolve(first.id).resolve("payload")))
            assertEquals(1L, Files.list(root).use { it.count() })
            reopened.delete(first)
            assertEquals(AttachmentResult.Replay, reopened.save(first, ByteReadChannel(bytes)))
            assertFalse(Files.exists(reopened.payload(first)))
        } finally {
            root.toFile().deleteRecursively()
        }
        Unit
    }

    @Test
    fun diskWriteFailureRemovesPartialFileAndReleasesUploadSlot() = runBlocking {
        val root = Files.createTempDirectory("laninbox-failed-attachment")
        try {
            var fail = true
            val store = DesktopAttachmentStore(root) { file ->
                val output = Files.newOutputStream(file)
                if (!fail) output else object : FilterOutputStream(output) {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        out.write(bytes[offset].toInt())
                        throw IOException("Simulated disk full")
                    }
                }
            }
            val bytes = byteArrayOf(1, 2, 3)
            assertFailsWith<IOException> { store.save(request(bytes), ByteReadChannel(bytes)) }
            assertEquals(0L, Files.list(root).use { it.count() })
            fail = false
            assertEquals(AttachmentResult.Created, store.save(request(bytes), ByteReadChannel(bytes)))
        } finally {
            root.toFile().deleteRecursively()
        }
        Unit
    }

    @Test
    fun cancellationRemovesStagingAndReleasesUploadCapacity() = runBlocking {
        val root = Files.createTempDirectory("laninbox-canceled-attachment")
        try {
            val store = DesktopAttachmentStore(root)
            val bytes = byteArrayOf(1)
            val request = request(bytes)
            val first = launch { store.save(request, ByteChannel()) }
            val second = launch { store.save(request, ByteChannel()) }
            try {
                withTimeout(5_000) {
                    while (Files.list(root).use { it.count() } != 2L) delay(10)
                }
                assertEquals(AttachmentResult.Busy, store.save(request, ByteReadChannel(bytes)))
            } finally {
                first.cancelAndJoin()
                second.cancelAndJoin()
            }
            assertEquals(0L, Files.list(root).use { it.count() })
            assertEquals(AttachmentResult.Created, store.save(request, ByteReadChannel(bytes)))
        } finally {
            root.toFile().deleteRecursively()
        }
        Unit
    }

    private fun request(bytes: ByteArray): AttachmentInfo {
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        return AttachmentInfo("00000000-0000-4000-8000-000000000001", bytes.size.toLong(), hash)
    }
}
