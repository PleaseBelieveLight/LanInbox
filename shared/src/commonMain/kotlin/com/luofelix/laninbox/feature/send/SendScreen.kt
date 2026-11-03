package com.luofelix.laninbox.feature.send

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.AppUiState
import com.luofelix.laninbox.core.network.MessageKind
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SendScreen(
    state: AppUiState,
    onDraftChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onKindChange: (MessageKind) -> Unit,
    onSend: (() -> Unit)?,
) {
    if (onSend != null) {
        Text(stringResource(Res.string.development_warning))
        OutlinedTextField(
            value = state.address,
            onValueChange = onAddressChange,
            label = { Text(stringResource(Res.string.receiver_address)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.sending,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == state.kind,
                    onClick = { onKindChange(kind) },
                    enabled = !state.sending,
                    label = { Text(stringResource(kind.label())) },
                )
            }
        }
    }
    OutlinedTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        label = { Text(stringResource(Res.string.send_draft)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        enabled = !state.sending,
    )
    if (onSend == null) {
        Text(stringResource(Res.string.send_unavailable))
    } else {
        Button(onClick = onSend, enabled = !state.sending && state.draft.isNotBlank()) {
            Text(stringResource(if (state.sending) Res.string.sending else Res.string.tab_send))
        }
        state.result?.let {
            val label = when (it) {
                DeliveryResult.Delivered -> Res.string.delivered
                DeliveryResult.Timeout -> Res.string.delivery_timeout
                DeliveryResult.Unreachable -> Res.string.delivery_unreachable
                DeliveryResult.Rejected -> Res.string.delivery_rejected
                DeliveryResult.InvalidResponse -> Res.string.delivery_invalid_response
                DeliveryResult.PermissionRequired -> Res.string.delivery_permission
            }
            Text(stringResource(label))
        }
    }
}

internal fun MessageKind.label() = when (this) {
    MessageKind.Text -> Res.string.kind_text
    MessageKind.Link -> Res.string.kind_link
    MessageKind.Code -> Res.string.kind_code
}
