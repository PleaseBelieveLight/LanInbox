package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.luofelix.laninbox.AppDialog
import com.luofelix.laninbox.AppDialogPanel
import com.luofelix.laninbox.AppIcons
import com.luofelix.laninbox.AppPopupMenu
import com.luofelix.laninbox.LocalDesktopDialogs
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
    val desktopDialogs = LocalDesktopDialogs.current
    if (confirmingClear && clearHistory != null) {
        AppDialog(
            onDismissRequest = { confirmingClear = false },
            title = stringResource(Res.string.history_clear),
            icon = AppIcons.Delete,
            text = { Text(stringResource(Res.string.history_clear_confirmation)) },
            confirmLabel = stringResource(Res.string.history_clear),
            onConfirm = { confirmingClear = false; clearHistory() },
            dismissLabel = stringResource(Res.string.attachment_cancel),
            destructive = true,
        )
    }
    openMessage?.let { item ->
        val message = item.request
        AppDialog(
            onDismissRequest = { openMessage = null },
            title = stringResource(Res.string.message_details),
            subtitle = kindLabel(message.kind),
            icon = when (message.kind) {
                MessageKind.Text -> AppIcons.Document
                MessageKind.Link -> AppIcons.Link
                MessageKind.Code -> AppIcons.Code
            },
            width = 680.dp,
            text = {
                AppDialogPanel(padding = if (desktopDialogs) 18.dp else 16.dp) {
                    SelectionContainer {
                        Text(
                            message.content,
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = FontFamily.Monospace.takeIf { message.kind == MessageKind.Code },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmLabel = stringResource(Res.string.copy_message),
            onConfirm = {
                clipboard.setText(AnnotatedString(message.content))
                openMessage = null
                scope.launch { snackbar.showSnackbar(copiedLabel) }
            },
            dismissLabel = stringResource(Res.string.close),
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
    val desktopDialogs = LocalDesktopDialogs.current
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = more }) {
            Icon(AppIcons.More, null, Modifier.size(20.dp))
        }
        AppPopupMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            DropdownMenuItem(
                text = { Text(label, color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(AppIcons.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.widthIn(min = if (desktopDialogs) 200.dp else 216.dp).heightIn(min = 48.dp),
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
