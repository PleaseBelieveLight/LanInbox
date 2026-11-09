package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.AppDialog
import com.luofelix.laninbox.AppDialogPanel
import com.luofelix.laninbox.AppIcons
import com.luofelix.laninbox.LocalDesktopDialogs
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
    leading: (@Composable () -> Unit)? = null,
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
        leading = leading,
    )
    if (showingPreview && preview != null) {
        val result by produceState<Pair<ImageBitmap?, Boolean>>(null to false, attachment) {
            value = preview(attachment) to true
        }
        val desktopDialogs = LocalDesktopDialogs.current
        AppDialog(
            onDismissRequest = { showingPreview = false },
            title = attachment.name,
            subtitle = size,
            icon = AppIcons.Image,
            width = 760.dp,
            text = {
                val (image, ready) = result
                AppDialogPanel(padding = if (desktopDialogs) 16.dp else 12.dp) {
                    Box(
                        Modifier.fillMaxWidth().height(if (desktopDialogs) 288.dp else 224.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            image != null -> Image(image, attachment.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            ready -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(AppIcons.Image, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(Res.string.preview_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            },
            confirmLabel = stringResource(Res.string.close),
            onConfirm = { showingPreview = false },
        )
    }
}
