package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.safeAttachmentName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SharedLogicAndroidHostTest {

    @Test
    fun sanitizesDisplayNamesAndRejectsPathComponentsAsTransferIds() {
        assertEquals("__照片.png", safeAttachmentName("../\\照片.png"))
        assertFalse(AttachmentInfo("../payload", 1L, "a".repeat(64)).isValid())
    }
}
