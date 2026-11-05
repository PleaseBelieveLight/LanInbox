package com.luofelix.laninbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.feature.send.TransferStatus
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeviceButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.widthIn(max = 188.dp)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ClientConnectionDialog(
    paired: Boolean,
    remembered: Boolean,
    authorized: Boolean,
    status: TransferStatus?,
    invitationRejected: Boolean,
    knownAddress: String?,
    developmentAddress: String?,
    onDevelopmentAddress: ((String) -> Unit)?,
    onAuthorize: () -> Unit,
    onScan: () -> Unit,
    onReconnect: (String?) -> Unit,
    onPair: (String, String, String) -> Unit,
    onForget: () -> Unit,
    onClose: () -> Unit,
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var confirmingForget by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable(knownAddress) { mutableStateOf(knownAddress.orEmpty()) }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var debug by rememberSaveable { mutableStateOf(false) }
    var debugAddress by rememberSaveable(developmentAddress) { mutableStateOf(developmentAddress.orEmpty()) }
    val busy = status == TransferStatus.Preparing
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.connect_computer)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    paired -> Text(stringResource(Res.string.secure_paired))
                    !authorized -> {
                        Text(stringResource(Res.string.delivery_permission))
                        Button(onClick = onAuthorize) { Text(stringResource(Res.string.pairing_allow_network)) }
                    }
                    busy -> Text(stringResource(Res.string.connecting))
                    else -> {
                        if (status == TransferStatus.Failed) {
                            Text(stringResource(
                                if (invitationRejected) Res.string.pairing_scan_refresh else Res.string.secure_pair_failed,
                            ))
                        }
                        if (remembered) {
                            Button(onClick = { onReconnect(null) }) { Text(stringResource(Res.string.reconnect_desktop)) }
                            TextButton(onClick = onScan) { Text(stringResource(Res.string.scan_to_connect)) }
                        } else {
                            Button(onClick = onScan) { Text(stringResource(Res.string.scan_to_connect)) }
                        }
                        TextButton(onClick = { manual = !manual }) { Text(stringResource(Res.string.pairing_manual)) }
                        if (manual) {
                            OutlinedTextField(
                                address, { address = it }, label = { Text(stringResource(Res.string.receiver_address)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            if (remembered && address.isNotBlank()) {
                                TextButton(onClick = { onReconnect(address) }) {
                                    Text(stringResource(Res.string.reconnect_desktop))
                                }
                            }
                            OutlinedTextField(
                                fingerprint, { fingerprint = it }, label = { Text(stringResource(Res.string.secure_fingerprint)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                invitation, { invitation = it }, label = { Text(stringResource(Res.string.secure_invitation)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                enabled = address.isNotBlank() && fingerprint.isNotBlank() && invitation.isNotBlank(),
                                onClick = { onPair(address, fingerprint, invitation) },
                            ) { Text(stringResource(Res.string.secure_pair)) }
                        }
                        if (onDevelopmentAddress != null) {
                            TextButton(onClick = { debug = !debug }) { Text(stringResource(Res.string.development_mode)) }
                            if (debug) {
                                Text(stringResource(Res.string.development_warning))
                                OutlinedTextField(
                                    debugAddress, { debugAddress = it },
                                    label = { Text(stringResource(Res.string.receiver_address)) }, singleLine = true,
                                )
                                TextButton(onClick = {
                                    onDevelopmentAddress(debugAddress.trim())
                                    onClose()
                                }) { Text(stringResource(Res.string.save)) }
                            }
                        }
                    }
                }
                if (remembered && !busy) {
                    TextButton(onClick = { confirmingForget = true }) { Text(stringResource(Res.string.secure_disconnect)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.close)) } },
    )
    if (confirmingForget) {
        AlertDialog(
            onDismissRequest = { confirmingForget = false },
            title = { Text(stringResource(Res.string.pairing_confirm_forget)) },
            text = { Text(stringResource(Res.string.pairing_confirm_forget_detail)) },
            confirmButton = {
                TextButton(onClick = { confirmingForget = false; onForget() }) {
                    Text(stringResource(Res.string.secure_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingForget = false }) { Text(stringResource(Res.string.attachment_cancel)) }
            },
        )
    }
}

@Composable
fun PairingScanScreen(
    instruction: String,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    busy: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    preview: @Composable (Modifier) -> Unit,
) {
    LanInboxTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeContentPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val back = stringResource(Res.string.back)
                    val ink = MaterialTheme.colorScheme.onSurface
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                        Canvas(Modifier.size(24.dp)) {
                            val start = Offset(size.width * 0.2f, center.y)
                            val tip = Offset(size.width * 0.8f, center.y)
                            val stroke = 2.dp.toPx()
                            drawLine(ink, start, tip, stroke, StrokeCap.Round)
                            drawLine(ink, start, Offset(center.x, size.height * 0.2f), stroke, StrokeCap.Round)
                            drawLine(ink, start, Offset(center.x, size.height * 0.8f), stroke, StrokeCap.Round)
                        }
                    }
                    Text(
                        stringResource(Res.string.pairing_scan_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    val details = stringResource(Res.string.pairing_details)
                    IconButton(onClick = onDetails, modifier = Modifier.semantics { contentDescription = details }) {
                        Text("⋯", style = MaterialTheme.typography.titleLarge)
                    }
                }
                HorizontalDivider()
                Surface(
                    Modifier.fillMaxWidth().weight(1f),
                    color = Color(0xFF172333),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        preview(Modifier.fillMaxSize())
                        if (busy) {
                            Surface(shape = MaterialTheme.shapes.medium) {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(28.dp))
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(instruction, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    if (actionLabel != null) {
                        TextButton(onClick = onAction) { Text(actionLabel) }
                    }
                }
            }
        }
    }
}
