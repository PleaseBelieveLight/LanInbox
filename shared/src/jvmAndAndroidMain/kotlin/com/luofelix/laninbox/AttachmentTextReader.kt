package com.luofelix.laninbox

import com.luofelix.laninbox.feature.inbox.AttachmentTextPreview
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val maxPreviewCharacters = 65_536

/** Reads a bounded local text preview, recognizing UTF BOMs and legacy Chinese text; the caller closes [input]. */
fun readAttachmentTextPreview(input: InputStream): AttachmentTextPreview {
    val maxBytes = maxPreviewCharacters * 4 + 3
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4_096)
    while (output.size() < maxBytes) {
        val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
    }
    val truncated = input.read() != -1
    val bytes = output.toByteArray()
    val (charset, offset) = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        else -> Charsets.UTF_8 to 0
    }
    var decoded: String? = null
    // A byte limit can split the last character. Trim only that partial tail before trying legacy encoding.
    for (tail in 0..(if (truncated) 3 else 0)) {
        val length = bytes.size - offset - tail
        if (length < 0) break
        try {
            decoded = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, length)).toString()
            break
        } catch (_: CharacterCodingException) {
            // Try a complete UTF character boundary before falling back to common Windows Chinese text.
        }
    }
    val text = decoded ?: String(bytes, offset, bytes.size - offset, Charset.forName("GB18030"))
    var end = minOf(text.length, maxPreviewCharacters)
    if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end--
    return AttachmentTextPreview(text.substring(0, end), truncated || text.length > end)
}
