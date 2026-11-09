package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.AttachmentInfo
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.HexFormat
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopReceivedDateTest {
    @Test
    fun restoresReceiptDatesAndKeepsThemStableForRetriesUntilHistoryIsDeleted() = runBlocking {
        val root = Files.createTempDirectory("laninbox-received-date-")
        val bytes = "收到的文件".encodeToByteArray()
        val info = AttachmentInfo(
            id = "00000000-0000-4000-8000-000000000001", size = bytes.size.toLong(),
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
            name = "中文文件.txt", mediaType = "text/plain",
        )
        val directory = root.resolve(info.id)
        val receipt = directory.resolve("receipt")
        val receivedAt = 1_700_000_000_000L
        try {
            val store = DesktopAttachmentStore(root)
            assertEquals(AttachmentResult.Created, store.save(info, ByteReadChannel(bytes)))
            assertTrue(assertNotNull(store.receivedAt.value[info.id]) > 0)
            Files.setLastModifiedTime(receipt, FileTime.fromMillis(receivedAt))

            val reopened = DesktopAttachmentStore(root)
            reopened.initialize()
            assertEquals(receivedAt, reopened.receivedAt.value[info.id])
            assertEquals(AttachmentResult.Replay, reopened.save(info, ByteReadChannel(bytes)))
            assertEquals(receivedAt, reopened.receivedAt.value[info.id])
            assertEquals(receivedAt, Files.getLastModifiedTime(receipt).toMillis())

            reopened.delete(info)
            assertFalse(reopened.receivedAt.value.containsKey(info.id))
            assertEquals(AttachmentResult.Replay, reopened.save(info, ByteReadChannel(bytes)))
            assertFalse(reopened.receivedAt.value.containsKey(info.id))
            assertTrue(Files.isRegularFile(receipt))
        } finally {
            Files.deleteIfExists(directory.resolve("payload"))
            Files.deleteIfExists(receipt)
            Files.deleteIfExists(directory)
            Files.deleteIfExists(root)
        }
    }
}
