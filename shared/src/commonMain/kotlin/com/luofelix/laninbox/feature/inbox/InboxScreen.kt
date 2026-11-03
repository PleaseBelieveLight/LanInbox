package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.feature.send.label
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@Suppress("DEPRECATION")
internal fun InboxScreen(messages: List<InboxMessage>, status: ReceiverStatus) {
    val statusLabel = when (status) {
        ReceiverStatus.Disabled -> Res.string.receiver_disabled
        ReceiverStatus.Starting -> Res.string.receiver_starting
        ReceiverStatus.Listening -> Res.string.receiver_listening
        ReceiverStatus.IdentityListening -> Res.string.receiver_identity_listening
        ReceiverStatus.Failed -> Res.string.receiver_failed
    }
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(statusLabel)) }
        if (messages.isEmpty()) item { Text(stringResource(Res.string.inbox_empty)) }
        items(messages, key = { "${it.request.senderId}:${it.request.messageId}" }) { (message) ->
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
            }
        }
    }
}
