package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MAX_MESSAGE_BYTES
import com.luofelix.laninbox.feature.send.SelectedFile
import com.luofelix.laninbox.feature.send.canSubmitDraft
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendComposerTest {
    @Test
    fun rejectsEmptyDraftsAndBlocksSubmissionWhileBusy() {
        assertFalse(canSubmitDraft("", null, enabled = true))
        assertFalse(canSubmitDraft(" \n\t", null, enabled = true))
        assertFalse(canSubmitDraft("发送文字", null, enabled = false))
        assertFalse(canSubmitDraft("", SelectedFile("照片.jpg"), enabled = false))
        assertTrue(canSubmitDraft("发送文字", null, enabled = true))
    }

    @Test
    fun enforcesTheUtf8ByteLimitForTextAndAllowsAnAttachmentInstead() {
        val atLimit = "文".repeat(MAX_MESSAGE_BYTES / 3) + "a".repeat(MAX_MESSAGE_BYTES % 3)
        assertTrue(canSubmitDraft(atLimit, null, enabled = true))
        assertFalse(canSubmitDraft(atLimit + "a", null, enabled = true))
        assertTrue(canSubmitDraft(atLimit + "a", SelectedFile("文字.txt"), enabled = true))
    }
}
