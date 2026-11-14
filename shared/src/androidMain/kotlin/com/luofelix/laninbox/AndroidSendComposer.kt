package com.luofelix.laninbox

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    var expanded by rememberSaveable { mutableStateOf(state.draft.isNotBlank() || file != null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun collapse() {
        focusManager.clearFocus()
        keyboard?.hide()
        expanded = false
    }
    BackHandler(enabled = expanded) { collapse() }
    LaunchedEffect(file) {
        if (file != null) {
            expanded = true
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(state.result) {
        if (state.result == DeliveryResult.Delivered) collapse()
    }
    LaunchedEffect(outgoingStatus) {
        if (outgoingStatus == TransferStatus.Delivered) collapse()
    }

    Column(Modifier.animateContentSize(animationSpec = tween(180))) {
        // Let the field shrink before the action row when the keyboard reduces the available height.
        Row(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentAction(
                icon = if (expanded) AppIcons.ChevronDown else AppIcons.ChevronUp,
                label = stringResource(if (expanded) Res.string.composer_collapse else Res.string.composer_expand),
                enabled = true,
            ) {
                if (expanded) collapse() else expanded = true
            }
            Box(Modifier.weight(1f)) {
                if (file == null) {
                    // Keep one field across both layouts to preserve focus, selection, and the draft.
                    TextField(
                        value = state.draft,
                        onValueChange = model::editDraft,
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            if (it.isFocused) expanded = true
                        },
                        enabled = enabled,
                        placeholder = { Text(stringResource(Res.string.send_draft)) },
                        singleLine = !expanded,
                        maxLines = if (expanded) 4 else 1,
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
            IconButton(
                modifier = Modifier.size(48.dp),
                enabled = enabled && (file != null || (state.draft.isNotBlank() && !tooLong)),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                ),
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
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 60.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
