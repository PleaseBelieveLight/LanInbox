package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MAX_ATTACHMENT_BYTES
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.core.network.OutboundDelivery
import com.luofelix.laninbox.core.network.attachmentMediaType
import com.luofelix.laninbox.core.network.safeAttachmentName
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class QueuedDelivery(
    val owner: Long,
    val delivery: OutboundDelivery,
    val stagedFile: Path? = null,
    val transferred: Long = 0,
    val offered: Boolean = false,
)

/** Holds one pending delivery until the paired phone acknowledges local storage. */
internal class DesktopOutbox {
    private val pending = MutableStateFlow<QueuedDelivery?>(null)
    val delivery = pending.asStateFlow()
    private val completed = MutableStateFlow<String?>(null)
    val delivered = completed.asStateFlow()

    fun enqueueText(session: Long, content: String, kind: MessageKind): Boolean {
        val id = UUID.randomUUID().toString()
        val message = MessageRequest("desktop", id, kind, content)
        return message.isValid() && enqueue(QueuedDelivery(session, OutboundDelivery(id, message = message)))
    }

    suspend fun enqueueFile(session: Long, source: Path): Boolean = withContext(Dispatchers.IO) {
        val staged = Files.createTempFile("laninbox-outgoing-", ".part")
        var queued = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            Files.newInputStream(source).use { input ->
                Files.newOutputStream(staged).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_ATTACHMENT_BYTES) return@withContext false
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val id = UUID.randomUUID().toString()
            val attachment = AttachmentInfo(
                id, size, HexFormat.of().formatHex(digest.digest()),
                safeAttachmentName(source.fileName.toString()),
                attachmentMediaType(
                    Files.probeContentType(source) ?: java.net.URLConnection.guessContentTypeFromName(source.fileName.toString()),
                ),
            )
            queued = enqueue(
                QueuedDelivery(session, OutboundDelivery(id, attachment = attachment), staged),
                currentCoroutineContext()[Job],
            )
            queued
        } finally {
            if (!queued) delete(staged)
        }
    }

    @Synchronized
    private fun enqueue(entry: QueuedDelivery, staging: Job? = null): Boolean {
        // Coordinate with cancellation so a finished staging job cannot publish after the outbox was cleared.
        staging?.ensureActive()
        if (pending.value != null) return false
        completed.value = null
        pending.value = entry
        return true
    }

    fun next(session: Long): QueuedDelivery? = pending.value?.takeIf { it.owner == session }

    /** Records that the paired phone started fetching the pending delivery. */
    @Synchronized
    fun offer(session: Long): OutboundDelivery? {
        val entry = next(session) ?: return null
        if (!entry.offered) pending.value = entry.copy(offered = true)
        return entry.delivery
    }

    @Synchronized
    fun acknowledge(session: Long, id: String): Boolean {
        val entry = next(session)?.takeIf { it.delivery.id == id } ?: return false
        pending.value = null
        completed.value = id
        delete(entry.stagedFile)
        return true
    }

    @Synchronized
    fun cancel() {
        val entry = pending.value
        pending.value = null
        completed.value = null
        delete(entry?.stagedFile)
    }

    suspend fun writeFile(entry: QueuedDelivery, output: OutputStream, authorized: () -> Boolean) =
        withContext(Dispatchers.IO) {
            val staged = checkNotNull(entry.stagedFile)
            try {
                val buffer = ByteArray(64 * 1024)
                var bytes = 0L
                updateProgress(entry, bytes)
                Files.newInputStream(staged).use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (!authorized() || !isPending(entry)) throw IOException("Transfer canceled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        bytes += count
                        updateProgress(entry, bytes)
                    }
                }
            } finally {
                if (!isPending(entry)) delete(staged)
            }
        }

    private fun isPending(entry: QueuedDelivery): Boolean = pending.value?.delivery?.id == entry.delivery.id

    @Synchronized
    private fun updateProgress(entry: QueuedDelivery, bytes: Long) {
        if (isPending(entry)) pending.value = entry.copy(transferred = bytes)
    }

    private fun delete(path: Path?) {
        // Windows can hold the file open during a download. The response closes it before retrying deletion.
        if (path != null) runCatching { Files.deleteIfExists(path) }
    }
}
