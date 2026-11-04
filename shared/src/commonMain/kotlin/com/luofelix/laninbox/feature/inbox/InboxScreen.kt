package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.feature.send.label
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@Suppress("DEPRECATION")
internal fun InboxScreen(
    messages: List<InboxMessage>,
    status: ReceiverStatus,
    inboxActions: @Composable () -> Unit,
    attachments: List<AttachmentInfo>,
    previewAttachment: (suspend (AttachmentInfo) -> ImageBitmap?)?,
    exportAttachment: ((AttachmentInfo) -> Unit)?,
    deleteMessage: ((InboxMessage) -> Unit)?,
    deleteAttachment: ((AttachmentInfo) -> Unit)?,
    clearHistory: (() -> Unit)?,
) {
    val statusLabel = when (status) {
        ReceiverStatus.Disabled -> Res.string.receiver_disabled
        ReceiverStatus.Starting -> Res.string.receiver_starting
        ReceiverStatus.Listening -> Res.string.receiver_listening
        ReceiverStatus.IdentityListening -> Res.string.receiver_identity_listening
        ReceiverStatus.SecureListening -> Res.string.receiver_secure_listening
        ReceiverStatus.Foreground -> Res.string.receiver_foreground
        ReceiverStatus.Failed -> Res.string.receiver_failed
    }
    val clipboard = LocalClipboardManager.current
    var confirmingClear by remember { mutableStateOf(false) }
    if (confirmingClear && clearHistory != null) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(Res.string.history_clear)) },
            text = { Text(stringResource(Res.string.history_clear_confirmation)) },
            confirmButton = {
                TextButton(onClick = { confirmingClear = false; clearHistory() }) {
                    Text(stringResource(Res.string.history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text(stringResource(Res.string.attachment_cancel)) }
            },
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(statusLabel))
                inboxActions()
            }
        }
        if (clearHistory != null && (messages.isNotEmpty() || attachments.isNotEmpty())) {
            item {
                OutlinedButton(onClick = { confirmingClear = true }) { Text(stringResource(Res.string.history_clear)) }
            }
        }
        if (messages.isEmpty() && attachments.isEmpty()) item { Text(stringResource(Res.string.inbox_empty)) }
        items(attachments, key = { "attachment:${it.id}" }) { attachment ->
            AttachmentCard(attachment, previewAttachment, exportAttachment, deleteAttachment)
        }
        items(messages, key = { "message:${it.request.senderId}:${it.request.messageId}" }) { (message) ->
            Column {
                Text(stringResource(message.kind.label()), style = MaterialTheme.typography.titleSmall)
                Text(
                    message.content,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = if (message.kind == MessageKind.Code) FontFamily.Monospace else null,
                    maxLines = 8,
                )
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }) {
                    Text(stringResource(Res.string.copy_message))
                }
                deleteMessage?.let { delete ->
                    TextButton(onClick = { delete(InboxMessage(message)) }) {
                        Text(stringResource(Res.string.history_delete))
                    }
                }
            }
        }
    }
}
