package com.luofelix.laninbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luofelix.laninbox.core.network.MAX_MESSAGE_BYTES
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.MessageSender
import com.luofelix.laninbox.feature.send.SelectedFile
import com.luofelix.laninbox.feature.send.TransferFeedback
import com.luofelix.laninbox.feature.send.TransferStatus
import com.luofelix.laninbox.feature.send.fileSizeLabel
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AndroidSendComposer(
    senderId: String,
    initialDraft: String,
    sender: MessageSender?,
    pairedAddress: String?,
    peerIdentity: String?,
    developmentAddress: String?,
    file: SelectedFile?,
    outgoingStatus: TransferStatus?,
    outgoingProgress: Float?,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onPickFile: () -> Unit,
    onRemove: () -> Unit,
    onSendFile: () -> Unit,
    onCancelFile: () -> Unit,
    onConnect: () -> Unit,
) {
    val model = viewModel { AppViewModel(initialDraft) }
    val state by model.uiState.collectAsStateWithLifecycle()
    val fileBusy = outgoingStatus == TransferStatus.Preparing || outgoingStatus == TransferStatus.Sending
    val enabled = !state.sending && !fileBusy
    val connectRequired = sender == null || (pairedAddress == null && (file != null || developmentAddress == null))
    val textFailure = file == null && state.result != null && state.result != DeliveryResult.Delivered
    val retry = textFailure || (file != null &&
        (outgoingStatus == TransferStatus.Failed || outgoingStatus == TransferStatus.Canceled))
    val tooLong = remember(state.draft) { state.draft.encodeToByteArray().size > MAX_MESSAGE_BYTES }
    val sendLabel = stringResource(
        when {
            connectRequired -> Res.string.connect_to_send
            retry -> Res.string.attachment_retry
            else -> Res.string.tab_send
        },
    )

    Column {
        // Let the field shrink before the action row when the keyboard reduces the available height.
        Box(Modifier.weight(1f, fill = false)) {
            if (file == null) {
                TextField(
                    value = state.draft,
                    onValueChange = model::editDraft,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    placeholder = { Text(stringResource(Res.string.send_draft)) },
                    maxLines = 4,
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text(stringResource(Res.string.draft_too_long)) }
                    } else null,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                AndroidAttachmentDraft(file, enabled, onRemove)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                AttachmentAction(AppIcons.Image, stringResource(Res.string.attachment_pick_image), enabled) {
                    model.clearFeedback()
                    onPickImage()
                }
                AttachmentAction(AppIcons.Document, stringResource(Res.string.attachment_pick_document), enabled) {
                    model.clearFeedback()
                    onPickDocument()
                }
                AttachmentAction(AppIcons.Folder, stringResource(Res.string.attachment_pick_file), enabled) {
                    model.clearFeedback()
                    onPickFile()
                }
            }
            FilledIconButton(
                modifier = Modifier.size(48.dp),
                enabled = enabled && (file != null || (state.draft.isNotBlank() && !tooLong)),
                onClick = {
                    when {
                        connectRequired -> onConnect()
                        file != null -> onSendFile()
                        else -> model.send(senderId, sender, pairedAddress ?: developmentAddress.orEmpty(), peerIdentity)
                    }
                },
            ) {
                Icon(
                    imageVector = if (retry && !connectRequired) AppIcons.Retry else AppIcons.Send,
                    contentDescription = sendLabel,
                )
            }
        }
        val result = state.result
        if (file == null && (state.sending || result != null)) {
            val label = when {
                state.sending -> Res.string.sending
                result == DeliveryResult.Delivered -> Res.string.delivered
                result == DeliveryResult.PermissionRequired -> Res.string.delivery_permission
                result == DeliveryResult.Unreachable -> Res.string.delivery_unreachable
                else -> Res.string.attachment_failed
            }
            Text(
                text = stringResource(label),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (textFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (outgoingStatus != null) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                TransferFeedback(outgoingStatus, outgoingProgress, cancel = onCancelFile.takeIf { fileBusy })
            }
        }
    }
}

@Composable
private fun AndroidAttachmentDraft(file: SelectedFile, enabled: Boolean, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Document, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                file.size?.let { Text(fileSizeLabel(it), style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(AppIcons.Close, contentDescription = stringResource(Res.string.attachment_remove))
            }
        }
    }
}
