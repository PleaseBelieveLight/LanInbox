package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.AttachmentInfo
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.attachment_bytes
import laninbox.shared.generated.resources.attachment_save
import laninbox.shared.generated.resources.history_delete
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AttachmentCard(
    attachment: AttachmentInfo,
    preview: (suspend (AttachmentInfo) -> ImageBitmap?)?,
    export: ((AttachmentInfo) -> Unit)?,
    delete: ((AttachmentInfo) -> Unit)?,
) {
    Column {
        Text(attachment.name)
        Text(stringResource(Res.string.attachment_bytes, attachment.size))
        if (attachment.mediaType.startsWith("image/") && preview != null) {
            val image by produceState<ImageBitmap?>(null, attachment) { value = preview(attachment) }
            image?.let {
                Image(it, attachment.name, modifier = Modifier.sizeIn(maxWidth = 256.dp, maxHeight = 256.dp))
            }
        }
        export?.let { save ->
            OutlinedButton(onClick = { save(attachment) }) {
                Text(stringResource(Res.string.attachment_save))
            }
        }
        delete?.let { remove ->
            TextButton(onClick = { remove(attachment) }) { Text(stringResource(Res.string.history_delete)) }
        }
    }
}
