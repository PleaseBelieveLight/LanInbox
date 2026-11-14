package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AttachmentCard(
    attachment: AttachmentInfo,
    preview: (suspend (AttachmentInfo) -> ImageBitmap?)?,
    export: ((AttachmentInfo) -> Unit)?,
    delete: ((AttachmentInfo) -> Unit)?,
    leading: (@Composable () -> Unit)? = null,
    open: (suspend (AttachmentInfo) -> Boolean)? = null,
    localPreview: (@Composable (AttachmentInfo, () -> Unit) -> Unit)? = null,
    receivedDate: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var showingPreview by remember { mutableStateOf(false) }
    val canPreview = attachment.canPreviewImage() && preview != null
    val size = fileSizeLabel(attachment.size)
    val desktop = LocalDesktopDialogs.current
    val metadata = if (desktop) stringResource(Res.string.attachment_metadata, size, attachment.typeLabel()) else size
    InboxRow(
        title = attachment.name,
        subtitle = when {
            receivedDate != null -> stringResource(Res.string.attachment_metadata, metadata, receivedDate)
            desktop -> metadata
            canPreview -> stringResource(Res.string.preview_image, size)
            else -> size
        },
        onOpen = { showingPreview = true },
        onSave = export?.let { save -> { save(attachment) } },
        onDelete = delete?.let { { it(attachment) } },
        leading = leading ?: { AttachmentTypeIcon(attachment) },
        contentPadding = contentPadding,
    )
    if (showingPreview) {
        val scope = rememberCoroutineScope()
        var opening by remember { mutableStateOf(false) }
        var openFailed by remember { mutableStateOf(false) }
        var localPreviewFailed by remember { mutableStateOf(false) }
        val showLocalPreview = localPreview != null && !localPreviewFailed &&
            attachment.localPreviewKind() != LocalAttachmentPreviewKind.Unsupported
        val result by produceState<Pair<ImageBitmap?, Boolean>>(null to !canPreview, attachment, preview) {
            if (canPreview && preview != null) {
                value = try {
                    preview(attachment) to true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null to true
                }
            }
        }
        val desktopDialogs = LocalDesktopDialogs.current
        AppDialog(
            onDismissRequest = { showingPreview = false },
            title = attachment.name,
            subtitle = size,
            icon = attachment.typeIcon(),
            width = if (canPreview) 760.dp else 480.dp,
            text = {
                val (image, ready) = result
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (showLocalPreview && localPreview != null) {
                        localPreview(attachment) { localPreviewFailed = true }
                    } else AppDialogPanel(padding = if (desktopDialogs) 16.dp else 12.dp) {
                        Box(
                            Modifier.fillMaxWidth().then(
                                if (canPreview) Modifier.height(if (desktopDialogs) 288.dp else 224.dp)
                                else Modifier.heightIn(min = 144.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                image != null -> Image(image, attachment.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                ready -> Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(AppIcons.Document, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(Res.string.preview_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                    if (openFailed) {
                        Text(stringResource(Res.string.attachment_open_failed), color = MaterialTheme.colorScheme.error)
                    }
                    if (ready && image == null && !showLocalPreview) {
                        export?.let { save ->
                            TextButton(onClick = { save(attachment); showingPreview = false }, enabled = !opening) {
                                Text(stringResource(Res.string.attachment_save))
                            }
                        }
                    }
                }
            },
            confirmLabel = stringResource(when {
                opening -> Res.string.attachment_opening
                open != null -> Res.string.attachment_open_with
                else -> Res.string.close
            }),
            confirmEnabled = !opening,
            dismissLabel = if (open != null) stringResource(Res.string.close) else null,
            onConfirm = {
                if (open == null) showingPreview = false
                else if (!opening) scope.launch {
                    opening = true
                    openFailed = false
                    try {
                        if (open(attachment)) showingPreview = false
                        else openFailed = true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        openFailed = true
                    } finally {
                        opening = false
                    }
                }
            },
        )
    }
}
