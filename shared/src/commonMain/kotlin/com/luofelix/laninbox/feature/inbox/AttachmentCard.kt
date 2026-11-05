package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.feature.send.fileSizeLabel
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AttachmentCard(
    attachment: AttachmentInfo,
    preview: (suspend (AttachmentInfo) -> ImageBitmap?)?,
    export: ((AttachmentInfo) -> Unit)?,
    delete: ((AttachmentInfo) -> Unit)?,
) {
    var showingPreview by remember { mutableStateOf(false) }
    val canPreview = attachment.mediaType.startsWith("image/") && preview != null
    val size = fileSizeLabel(attachment.size)
    InboxRow(
        title = attachment.name,
        subtitle = if (canPreview) stringResource(Res.string.preview_image, size) else size,
        onOpen = if (canPreview) ({ showingPreview = true }) else null,
        action = {
            export?.let { save ->
                TextButton(onClick = { save(attachment) }) { Text(stringResource(Res.string.attachment_save)) }
            }
        },
        onDelete = delete?.let { { it(attachment) } },
    )
    if (showingPreview && preview != null) {
        val result by produceState<Pair<ImageBitmap?, Boolean>>(null to false, attachment) {
            value = preview(attachment) to true
        }
        AlertDialog(
            onDismissRequest = { showingPreview = false },
            title = { Text(attachment.name) },
            text = {
                val (image, ready) = result
                when {
                    image != null -> Image(
                        image, attachment.name, modifier = Modifier.sizeIn(maxWidth = 420.dp, maxHeight = 320.dp),
                    )
                    ready -> Text(stringResource(Res.string.preview_unavailable))
                    else -> CircularProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(onClick = { showingPreview = false }) { Text(stringResource(Res.string.close)) }
            },
        )
    }
}
