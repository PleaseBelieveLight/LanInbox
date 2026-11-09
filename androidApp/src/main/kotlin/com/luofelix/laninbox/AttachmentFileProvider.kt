package com.luofelix.laninbox

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider

/** Resolves the original display name because stored payloads intentionally have no media extension. */
class AttachmentFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        val fallback = super.getType(uri)
        val name = uri.getQueryParameter("displayName")
        val type = name?.substringAfterLast('.', "")?.lowercase()
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return type ?: fallback
    }
}
