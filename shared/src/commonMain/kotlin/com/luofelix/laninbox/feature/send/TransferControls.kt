package com.luofelix.laninbox.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.MessageKind
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.attachment_cancel
import laninbox.shared.generated.resources.attachment_canceled
import laninbox.shared.generated.resources.attachment_choose
import laninbox.shared.generated.resources.attachment_failed
import laninbox.shared.generated.resources.attachment_pair_required
import laninbox.shared.generated.resources.attachment_preparing
import laninbox.shared.generated.resources.attachment_receiving
import laninbox.shared.generated.resources.attachment_retry
import laninbox.shared.generated.resources.delivered
import laninbox.shared.generated.resources.delivery_permission
import laninbox.shared.generated.resources.device_name
import laninbox.shared.generated.resources.discover_desktops
import laninbox.shared.generated.resources.receiver_address
import laninbox.shared.generated.resources.reconnect_desktop
import laninbox.shared.generated.resources.save_device_name
import laninbox.shared.generated.resources.scan_pairing_qr
import laninbox.shared.generated.resources.secure_disconnect
import laninbox.shared.generated.resources.secure_fingerprint
import laninbox.shared.generated.resources.secure_invitation
import laninbox.shared.generated.resources.secure_pair
import laninbox.shared.generated.resources.secure_pair_failed
import laninbox.shared.generated.resources.secure_paired
import laninbox.shared.generated.resources.send_draft
import laninbox.shared.generated.resources.send_to_phone
import laninbox.shared.generated.resources.sending
import org.jetbrains.compose.resources.stringResource

enum class TransferStatus { Preparing, Sending, Receiving, Delivered, Failed, Canceled, PermissionRequired }

@Composable
fun TransferControls(
    status: TransferStatus?,
    progress: Float? = null,
    name: String? = null,
    choose: (() -> Unit)? = null,
    chooseEnabled: Boolean = true,
    pairingRequired: Boolean = false,
    cancel: (() -> Unit)? = null,
    retry: (() -> Unit)? = null,
) {
    name?.let { Text(it) }
    status?.let {
        val label = when (it) {
            TransferStatus.Preparing -> Res.string.attachment_preparing
            TransferStatus.Sending -> Res.string.sending
            TransferStatus.Receiving -> Res.string.attachment_receiving
            TransferStatus.Delivered -> Res.string.delivered
            TransferStatus.Failed -> Res.string.attachment_failed
            TransferStatus.Canceled -> Res.string.attachment_canceled
            TransferStatus.PermissionRequired -> Res.string.delivery_permission
        }
        Text(stringResource(label))
        if (it == TransferStatus.Sending || it == TransferStatus.Receiving) {
            if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (pairingRequired) Text(stringResource(Res.string.attachment_pair_required))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choose?.let {
            OutlinedButton(onClick = it, enabled = chooseEnabled && !pairingRequired) {
                Text(stringResource(Res.string.attachment_choose))
            }
        }
        cancel?.let { OutlinedButton(onClick = it) { Text(stringResource(Res.string.attachment_cancel)) } }
        retry?.let { OutlinedButton(onClick = it) { Text(stringResource(Res.string.attachment_retry)) } }
    }
}

@Composable
fun DesktopTextSend(enabled: Boolean, onSend: (String, MessageKind) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MessageKind.Text) }
    var rejected by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; rejected = false },
        label = { Text(stringResource(Res.string.send_draft)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageKind.entries.forEach {
            FilterChip(selected = kind == it, onClick = { kind = it }, label = { Text(stringResource(it.label())) })
        }
    }
    Button(enabled = enabled && text.isNotBlank(), onClick = {
        if (onSend(text, kind)) text = "" else rejected = true
    }) { Text(stringResource(Res.string.send_to_phone)) }
    if (rejected) Text(stringResource(Res.string.attachment_failed))
}

@Composable
fun SecurePairingForm(
    connected: Boolean,
    busy: Boolean,
    failed: Boolean,
    onPair: (address: String, fingerprint: String, invitation: String) -> Unit,
    onDisconnect: () -> Unit,
    devices: List<Pair<String, String>> = emptyList(),
    remembered: Boolean = false,
    onDiscover: (() -> Unit)? = null,
    onScan: (() -> Unit)? = null,
    onReconnect: ((String) -> Unit)? = null,
) {
    var address by remember { mutableStateOf("") }
    var fingerprint by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    if (connected) {
        Text(stringResource(Res.string.secure_paired))
        OutlinedButton(onClick = onDisconnect) { Text(stringResource(Res.string.secure_disconnect)) }
    } else {
        onDiscover?.let {
            OutlinedButton(onClick = it) { Text(stringResource(Res.string.discover_desktops)) }
        }
        devices.forEach { (name, endpoint) ->
            TextButton(enabled = !busy, onClick = { address = endpoint }) {
                Text("$name · $endpoint")
            }
        }
        onScan?.let {
            OutlinedButton(enabled = !busy, onClick = it) { Text(stringResource(Res.string.scan_pairing_qr)) }
        }
        OutlinedTextField(
            address, { address = it }, label = { Text(stringResource(Res.string.receiver_address)) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            fingerprint, { fingerprint = it }, label = { Text(stringResource(Res.string.secure_fingerprint)) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            invitation, { invitation = it }, label = { Text(stringResource(Res.string.secure_invitation)) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            enabled = !busy && address.isNotBlank() && fingerprint.isNotBlank() && invitation.isNotBlank(),
            onClick = { onPair(address, fingerprint, invitation) },
        ) { Text(stringResource(Res.string.secure_pair)) }
        if (remembered && onReconnect != null) {
            OutlinedButton(enabled = !busy, onClick = { onReconnect(address) }) {
                Text(stringResource(Res.string.reconnect_desktop))
            }
        }
        if (failed) Text(stringResource(Res.string.secure_pair_failed))
    }
}

@Composable
fun DeviceNameEditor(name: String, onSave: (String) -> Unit) {
    var draft by remember(name) { mutableStateOf(name) }
    OutlinedTextField(
        draft, { draft = it.take(40) }, label = { Text(stringResource(Res.string.device_name)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    if (draft.trim() != name && draft.isNotBlank()) {
        OutlinedButton(onClick = { onSave(draft.trim()) }) { Text(stringResource(Res.string.save_device_name)) }
    }
}
