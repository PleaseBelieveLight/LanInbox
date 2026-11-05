package com.luofelix.laninbox.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class TransferStatus {
    Preparing, Waiting, Sending, Receiving, Delivered, Failed, Canceled, PermissionRequired,
}

@Composable
fun TransferFeedback(
    status: TransferStatus?,
    progress: Float? = null,
    cancel: (() -> Unit)? = null,
    retry: (() -> Unit)? = null,
) {
    if (status == null) return
    val label = when (status) {
        TransferStatus.Preparing -> Res.string.attachment_preparing
        TransferStatus.Waiting -> Res.string.transfer_waiting
        TransferStatus.Sending -> Res.string.sending
        TransferStatus.Receiving -> Res.string.attachment_receiving
        TransferStatus.Delivered -> Res.string.delivered
        TransferStatus.Failed -> Res.string.attachment_failed
        TransferStatus.Canceled -> Res.string.attachment_canceled
        TransferStatus.PermissionRequired -> Res.string.delivery_permission
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(label),
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
            cancel?.let { TextButton(onClick = it) { Text(stringResource(Res.string.attachment_cancel)) } }
            retry?.let { TextButton(onClick = it) { Text(stringResource(Res.string.attachment_retry)) } }
        }
        if (status == TransferStatus.Sending || status == TransferStatus.Receiving) {
            if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
