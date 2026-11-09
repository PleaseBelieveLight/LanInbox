package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MAX_ATTACHMENT_BYTES
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class AttachmentResult {
    Created, Replay, Conflict, InvalidBody, TooLarge, Busy,
}

/** Stores verified payloads under their transfer IDs and publishes each payload with its receipt. */
internal class DesktopAttachmentStore(
    private val root: Path,
    private val openStagingOutput: (Path) -> OutputStream = { Files.newOutputStream(it) },
) {
    private val uploads = Semaphore(2)
    private val publication = Mutex()
    private val history = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments = history.asStateFlow()
    private val receivedDates = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Local completion times in epoch milliseconds; replays leave the original receipt time unchanged. */
    val receivedAt = receivedDates.asStateFlow()

    fun payload(attachment: AttachmentInfo): Path {
        require(attachment.isValid())
        return root.resolve(attachment.id).resolve("payload")
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (Files.isDirectory(root)) {
            val staleBefore = Instant.now().minus(1, ChronoUnit.DAYS)
            val restored = Files.list(root).use { entries ->
                entries.filter { Files.isDirectory(it) }.filter {
                    if (it.fileName.toString().startsWith(".upload-")) {
                        if (Files.getLastModifiedTime(it).toInstant().isBefore(staleBefore)) {
                            cleanupStaging(it, it.resolve("payload"), it.resolve("receipt"))
                        }
                        false
                    } else Files.isRegularFile(it.resolve("payload"))
                }
                    .sorted(compareByDescending { Files.getLastModifiedTime(it) })
                    .limit(100)
                    .map(::readReceipt)
                    .toList()
                    .filterNotNull()
            }
            receivedDates.value = restored.associate {
                it.id to Files.getLastModifiedTime(root.resolve(it.id).resolve("receipt")).toMillis()
            }.filterValues { it > 0 }
            history.value = restored
        }
    }

    /** Removes stored payloads while keeping the size and digest needed to acknowledge retries. */
    suspend fun delete(attachment: AttachmentInfo?) = withContext(Dispatchers.IO) {
        publication.withLock {
            val candidates = when {
                !Files.isDirectory(root) -> emptyList()
                attachment == null -> Files.list(root).use { entries ->
                    entries.map(::readReceipt).toList().filterNotNull()
                }
                else -> listOf(attachment)
            }
            for (item in candidates) {
                Files.deleteIfExists(payload(item))
                history.value = history.value.filterNot { it.id == item.id }
                receivedDates.value = receivedDates.value - item.id
            }
        }
    }

    /**
     * Returns [AttachmentResult.Replay] for a verified retry with matching length and digest.
     * Cancellation discards the staging files. An upload already published can be retried safely.
     */
    suspend fun save(request: AttachmentInfo, body: ByteReadChannel): AttachmentResult {
        require(request.isValid())
        if (!uploads.tryAcquire()) return AttachmentResult.Busy
        try {
            return withContext(Dispatchers.IO) { saveOnDisk(request, body) }
        } finally {
            uploads.release()
        }
    }

    private suspend fun saveOnDisk(
        request: AttachmentInfo,
        body: ByteReadChannel,
    ): AttachmentResult {
        Files.createDirectories(root)
        val staging = Files.createTempDirectory(root, ".upload-")
        val payload = staging.resolve("payload")
        val receipt = staging.resolve("receipt")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var received = 0L
            openStagingOutput(payload).use { output ->
                while (true) {
                    val count = body.readAvailable(buffer)
                    if (count == -1) break
                    received += count
                    if (received > MAX_ATTACHMENT_BYTES) return AttachmentResult.TooLarge
                    if (received > request.size) return AttachmentResult.InvalidBody
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            if (received != request.size || HexFormat.of().formatHex(digest.digest()) != request.sha256) {
                return AttachmentResult.InvalidBody
            }

            Files.write(receipt, listOf(request.size.toString(), request.sha256, request.name, request.mediaType))
            // The immutable receipt records local completion time, independent of retries and external copies.
            val receivedAtMillis = Files.getLastModifiedTime(receipt).toMillis()
            return publication.withLock {
                currentCoroutineContext().ensureActive()
                val target = root.resolve(request.id)
                if (Files.isDirectory(target)) {
                    val previous = readReceipt(target)
                    if (previous?.size == request.size && previous.sha256 == request.sha256) {
                        AttachmentResult.Replay
                    } else {
                        AttachmentResult.Conflict
                    }
                } else {
                    // Publish the payload and receipt together on the same filesystem.
                    Files.move(staging, target, ATOMIC_MOVE)
                    val recent = (listOf(request) + history.value).take(100)
                    val ids = recent.mapTo(mutableSetOf()) { it.id }
                    receivedDates.value = (receivedDates.value + (request.id to receivedAtMillis)).filterKeys { it in ids }
                    history.value = recent
                    AttachmentResult.Created
                }
            }
        } finally {
            cleanupStaging(staging, payload, receipt)
        }
    }

    private fun readReceipt(directory: Path): AttachmentInfo? {
        val receipt = directory.resolve("receipt")
        if (!Files.isRegularFile(receipt) || Files.size(receipt) > 2_048) return null
        val lines = Files.readAllLines(receipt)
        val attachment = AttachmentInfo(
            id = directory.fileName.toString(),
            size = lines.getOrNull(0)?.toLongOrNull() ?: return null,
            sha256 = lines.getOrNull(1) ?: return null,
            name = lines.getOrNull(2) ?: "attachment",
            mediaType = lines.getOrNull(3) ?: "application/octet-stream",
        )
        return attachment.takeIf { it.isValid() }
    }

    private fun cleanupStaging(staging: Path, payload: Path, receipt: Path) {
        try {
            Files.deleteIfExists(payload)
            Files.deleteIfExists(receipt)
            Files.deleteIfExists(staging)
        } catch (_: IOException) {
            // A failed cleanup must not hide cancellation or the original storage error.
        }
    }
}
