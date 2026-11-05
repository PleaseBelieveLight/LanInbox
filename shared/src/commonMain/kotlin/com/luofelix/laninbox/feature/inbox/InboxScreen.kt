package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MessageKind
import kotlinx.coroutines.launch
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@Suppress("DEPRECATION")
internal fun InboxScreen(
    messages: List<InboxMessage>,
    attachments: List<AttachmentInfo>,
    previewAttachment: (suspend (AttachmentInfo) -> ImageBitmap?)?,
    exportAttachment: ((AttachmentInfo) -> Unit)?,
    deleteMessage: ((InboxMessage) -> Unit)?,
    deleteAttachment: ((AttachmentInfo) -> Unit)?,
    clearHistory: (() -> Unit)?,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    messageIcon: (@Composable (MessageKind) -> Unit)? = null,
    attachmentIcon: (@Composable (AttachmentInfo) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedLabel = stringResource(Res.string.message_copied)
    var confirmingClear by remember { mutableStateOf(false) }
    var openMessage by remember { mutableStateOf<InboxMessage?>(null) }
    val hasHistory = messages.isNotEmpty() || attachments.isNotEmpty()
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
    openMessage?.let { item ->
        val message = item.request
        AlertDialog(
            onDismissRequest = { openMessage = null },
            title = { Text(kindLabel(message.kind)) },
            text = {
                SelectionContainer {
                    Text(
                        message.content,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace.takeIf { message.kind == MessageKind.Code },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    openMessage = null
                    scope.launch { snackbar.showSnackbar(copiedLabel) }
                }) { Text(stringResource(Res.string.copy_message)) }
            },
            dismissButton = {
                TextButton(onClick = { openMessage = null }) { Text(stringResource(Res.string.close)) }
            },
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.tab_inbox),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (clearHistory != null && hasHistory) {
                InboxMenu(stringResource(Res.string.history_clear)) { confirmingClear = true }
            }
        }
        status()
        LazyColumn(Modifier.weight(1f)) {
            if (!hasHistory) {
                item {
                    Text(
                        stringResource(Res.string.inbox_empty),
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(messages, key = { "message:${it.request.senderId}:${it.request.messageId}" }) { item ->
                val message = item.request
                InboxRow(
                    title = message.content,
                    subtitle = kindLabel(message.kind),
                    code = message.kind == MessageKind.Code,
                    onOpen = { openMessage = item },
                    action = {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(message.content))
                            scope.launch { snackbar.showSnackbar(copiedLabel) }
                        }) { Text(stringResource(Res.string.copy_message)) }
                    },
                    onDelete = deleteMessage?.let { { it(item) } },
                    leading = if (messageIcon != null) ({ messageIcon(message.kind) }) else null,
                )
            }
            items(attachments, key = { "attachment:${it.id}" }) { attachment ->
                AttachmentCard(
                    attachment, previewAttachment, exportAttachment, deleteAttachment,
                    leading = if (attachmentIcon != null) ({ attachmentIcon(attachment) }) else null,
                )
            }
        }
        SnackbarHost(snackbar)
    }
}

@Composable
private fun kindLabel(kind: MessageKind): String = stringResource(
    when (kind) {
        MessageKind.Text -> Res.string.kind_text
        MessageKind.Link -> Res.string.kind_link
        MessageKind.Code -> Res.string.kind_code
    },
)

@Composable
internal fun InboxRow(
    title: String,
    subtitle: String,
    code: Boolean = false,
    onOpen: (() -> Unit)?,
    action: @Composable () -> Unit,
    onDelete: (() -> Unit)?,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            Modifier.weight(1f)
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace.takeIf { code },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        action()
        onDelete?.let { InboxMenu(stringResource(Res.string.history_delete), it) }
    }
    HorizontalDivider()
}

@Composable
private fun InboxMenu(label: String, action: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val more = stringResource(Res.string.more)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = more }) {
            Text("⋯", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { open = false; action() },
            )
        }
    }
}

@Composable
fun InboxOperationError() {
    Text(
        stringResource(Res.string.receive_error),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
