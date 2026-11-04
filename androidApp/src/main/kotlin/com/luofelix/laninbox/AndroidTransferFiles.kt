package com.luofelix.laninbox

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MAX_ATTACHMENT_BYTES
import com.luofelix.laninbox.core.network.MAX_REQUEST_BYTES
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.OutboundDelivery
import com.luofelix.laninbox.core.network.attachmentMediaType
import com.luofelix.laninbox.core.network.safeAttachmentName
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString

internal data class StagedUpload(val info: AttachmentInfo, val file: File)

internal class AndroidTransferFiles(private val application: Application) {
    private val inbox = File(application.filesDir, "secure-inbox")
    private val resolver = application.contentResolver
    private val historyLock = Mutex()

    suspend fun stage(uri: Uri): StagedUpload = withContext(Dispatchers.IO) {
        if (uri.scheme != "content" && uri.scheme != "file") throw IOException("Unsupported local URI")
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val media = attachmentMediaType(resolver.getType(uri) ?: java.net.URLConnection.guessContentTypeFromName(name))
        val staged = File.createTempFile("laninbox-upload-", ".part", application.cacheDir)
        var complete = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the selected file")
            input.use {
                staged.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_ATTACHMENT_BYTES) throw IOException("Attachment exceeds size limit")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val info = AttachmentInfo(
                UUID.randomUUID().toString(), size, digest.digest().toByteString().hex(), safeAttachmentName(name), media,
            )
            currentCoroutineContext().ensureActive()
            complete = true
            StagedUpload(info, staged)
        } finally {
            if (!complete) staged.delete()
        }
    }

    suspend fun recent(): List<OutboundDelivery> = withContext(Dispatchers.IO) {
        inbox.listFiles().orEmpty()
            .filter { it.extension == "json" }
            .filter { !File(inbox, "${it.nameWithoutExtension}.deleted").exists() }
            .sortedByDescending { it.lastModified() }
            .asSequence()
            .mapNotNull(::readRecord)
            .take(100)
            .toList()
    }

    suspend fun receive(client: SecureLanClient, delivery: OutboundDelivery, progress: (Long) -> Unit) =
        withContext(Dispatchers.IO) { historyLock.withLock {
            require(delivery.isValid())
            if (!inbox.isDirectory && !inbox.mkdirs()) throw IOException("Could not create inbox")
            val record = File(inbox, "${delivery.id}.json")
            val tombstone = File(inbox, "${delivery.id}.deleted")
            if (tombstone.isFile) {
                if (tombstone.readText() == deliveryDigest(delivery)) return@withLock
                throw IOException("Delivery ID conflict")
            }
            val saved = readRecord(record)
            if (saved == delivery) return@withLock
            if (saved != null) throw IOException("Delivery ID conflict")
            val attachment = delivery.attachment
            if (attachment != null) {
                val staged = File.createTempFile("laninbox-download-", ".part", inbox)
                try {
                    client.download(attachment, staged, progress)
                    currentCoroutineContext().ensureActive()
                    if (!staged.renameTo(payload(attachment))) throw IOException("Could not publish attachment")
                } finally {
                    staged.delete()
                }
            }

            // Publish the receipt before the phone acknowledges, including after an acknowledgment loss.
            val atomic = AtomicFile(record)
            val stream = atomic.startWrite()
            try {
                stream.write(MessageJson.encodeToString(delivery).encodeToByteArray())
                atomic.finishWrite(stream)
            } catch (error: IOException) {
                atomic.failWrite(stream)
                throw error
            }
            if (readRecord(record) != delivery) throw IOException("Could not commit inbox receipt")
        } }

    /** Retains only the retry digest after deleting the original content. */
    suspend fun delete(id: String?) = withContext(Dispatchers.IO) {
        historyLock.withLock {
            val entries = inbox.listFiles().orEmpty().filter {
                it.extension == "json" && (id == null || it.nameWithoutExtension == id)
            }
            for (file in entries) {
                val delivery = readRecord(file) ?: continue
                val tombstone = AtomicFile(File(inbox, "${delivery.id}.deleted"))
                val stream = tombstone.startWrite()
                try {
                    stream.write(deliveryDigest(delivery).encodeToByteArray())
                    tombstone.finishWrite(stream)
                } catch (error: IOException) {
                    tombstone.failWrite(stream)
                    throw error
                }
                if (tombstone.readFully().decodeToString() != deliveryDigest(delivery)) {
                    throw IOException("Could not commit deleted delivery receipt")
                }
                delivery.attachment?.let {
                    val stored = payload(it)
                    if (!stored.delete() && stored.exists()) throw IOException("Could not delete attachment")
                }
                if (!file.delete() && file.exists()) throw IOException("Could not delete history record")
            }
        }
    }

    private fun deliveryDigest(delivery: OutboundDelivery): String = MessageDigest.getInstance("SHA-256")
        .digest(MessageJson.encodeToString(delivery).encodeToByteArray()).toByteString().hex()

    suspend fun preview(attachment: AttachmentInfo): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = payload(attachment).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000) return@withContext null
        var sample = 1
        while (maxOf(width, height) / sample > 512) sample *= 2
        BitmapFactory.decodeFile(file, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }

    suspend fun export(attachment: AttachmentInfo, uri: Uri) = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open save destination")
        output.use { destination -> payload(attachment).inputStream().use { it.copyTo(destination) } }
    }

    private fun payload(attachment: AttachmentInfo): File {
        require(attachment.isValid())
        return File(inbox, "${attachment.id}.payload")
    }

    private fun readRecord(file: File): OutboundDelivery? {
        if (!file.isFile || file.length() > MAX_REQUEST_BYTES) return null
        return runCatching {
            MessageJson.decodeFromString<OutboundDelivery>(AtomicFile(file).readFully().decodeToString())
                .takeIf {
                    it.isValid() && (it.attachment?.let { item -> payload(item).length() == item.size &&
                        payload(item).isFile } != false)
                }
        }.getOrNull()
    }
}
