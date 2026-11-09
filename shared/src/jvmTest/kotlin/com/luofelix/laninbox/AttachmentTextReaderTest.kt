package com.luofelix.laninbox

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentTextReaderTest {
    @Test
    fun readsUtf8ChineseTextAndStripsItsOptionalBom() {
        val text = "本地文档\n第二行 😀"
        val plain = readAttachmentTextPreview(ByteArrayInputStream(text.toByteArray()))
        val withBom = readAttachmentTextPreview(ByteArrayInputStream(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()))
        assertEquals(text, plain.text)
        assertEquals(plain, withBom)
        assertFalse(plain.truncated)
    }

    @Test
    fun readsUtf16AndLegacyWindowsChineseText() {
        val text = "本地文件\r\n中文内容"
        val littleEndian = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val bigEndian = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        listOf(littleEndian, bigEndian, text.toByteArray(Charset.forName("GB18030"))).forEach {
            assertEquals(text, readAttachmentTextPreview(ByteArrayInputStream(it)).text)
        }
    }

    @Test
    fun boundsLargeFilesAndMarksTruncationWithoutReadingTheWholeInput() {
        val bytes = "中文内容".repeat(100_000).toByteArray()
        val input = ByteArrayInputStream(bytes)
        val result = readAttachmentTextPreview(input)
        assertEquals(65_536, result.text.length)
        assertTrue(result.text.startsWith("中文内容"))
        assertTrue(result.truncated)
        assertTrue(input.available() > bytes.size / 2)
    }

    @Test
    fun acceptsAnEmptyTextFile() {
        val result = readAttachmentTextPreview(ByteArrayInputStream(byteArrayOf()))
        assertEquals("", result.text)
        assertFalse(result.truncated)
    }

    @Test
    fun doesNotSplitEmojiAtTheCharacterLimit() {
        val text = "a".repeat(65_535) + "😀more"
        val result = readAttachmentTextPreview(ByteArrayInputStream(text.toByteArray()))
        assertEquals("a".repeat(65_535), result.text)
        assertTrue(result.truncated)
    }
}
