package com.luofelix.laninbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.PairingInvitation
import com.luofelix.laninbox.core.network.SECURE_PORT
import com.luofelix.laninbox.core.network.privateLanAuthority
import kotlinx.coroutines.delay
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopConnection(
    shown: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    receiverReady: Boolean,
    host: String?,
    fingerprint: String,
    pairedSession: Long?,
    pairing: DesktopPairingSession,
    state: DesktopTransferState,
    deviceName: String,
    onDeviceName: (String) -> Unit,
) {
    val paired = pairedSession != null
    val connectedSession by pairing.connectedSession.collectAsState()
    val connected = receiverReady && paired && connectedSession == pairedSession
    val confirmedPairing by pairing.confirmedPairing.collectAsState()
    var acknowledgedPairing by remember(pairing) { mutableStateOf<Long?>(null) }
    val pairingSucceeded = connected && confirmedPairing != null && confirmedPairing == pairedSession &&
        confirmedPairing != acknowledgedPairing
    OutlinedButton(onClick = onOpen) {
        Icon(AppIcons.Phone, null, Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(when {
            connected -> Res.string.connected_phone
            paired -> Res.string.paired_phone
            else -> Res.string.connect_phone
        }))
    }

    val code by pairing.invitation.collectAsState()
    LaunchedEffect(code) {
        code?.let {
            delay(DesktopPairingSession.INVITATION_LIFETIME)
            pairing.expireInvitation(it)
        }
    }
    if (!shown) return

    val address = host?.let { "$it:$SECURE_PORT" }
    val qrAddress = address?.let { privateLanAuthority(it, SECURE_PORT) }
    val invitation = code
    var replacingPairing by remember(pairedSession) { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var confirmingForget by remember { mutableStateOf(false) }
    var name by remember(deviceName) { mutableStateOf(deviceName) }
    LaunchedEffect(receiverReady, address, paired) {
        if (receiverReady && address != null && !paired && pairing.invitation.value == null) pairing.issueInvitation()
    }
    val qr = remember(invitation, qrAddress, fingerprint) {
        if (invitation != null && qrAddress != null) pairingQr(PairingInvitation(qrAddress, fingerprint, invitation))
        else null
    }
    fun closeConnection() {
        if (pairingSucceeded) acknowledgedPairing = confirmedPairing
        onClose()
    }
    val colors = MaterialTheme.colorScheme
    AppDialog(
        onDismissRequest = ::closeConnection,
        title = stringResource(when {
            pairingSucceeded -> Res.string.pairing_success
            replacingPairing -> Res.string.pairing_again
            connected -> Res.string.connected_phone
            paired -> Res.string.paired_phone
            else -> Res.string.connect_phone
        }),
        subtitle = deviceName,
        icon = when {
            replacingPairing -> AppIcons.Scan
            pairingSucceeded || connected -> AppIcons.Check
            else -> AppIcons.Phone
        },
        width = 520.dp,
        confirmLabel = stringResource(if (pairingSucceeded) Res.string.pairing_done else Res.string.close),
        onConfirm = ::closeConnection,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (replacingPairing && !pairingSucceeded) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryContainer) {
                    Text(
                        stringResource(Res.string.pairing_replacement_hint),
                        Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                    )
                }
            }
            when {
                pairingSucceeded || paired && !replacingPairing -> {
                    ConnectionStatusCard(
                        connected = connected,
                        title = stringResource(if (connected) Res.string.device_connected else Res.string.pairing_saved),
                        detail = stringResource(when {
                            pairingSucceeded -> Res.string.pairing_success_detail
                            connected -> Res.string.connected_phone_detail
                            else -> Res.string.paired_phone_detail
                        }),
                    )
                    if (!pairingSucceeded) {
                        FilledTonalButton(
                            enabled = receiverReady && address != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                replacingPairing = true
                                pairing.issueInvitation()
                            },
                        ) {
                            Icon(AppIcons.Retry, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.pairing_again))
                        }
                        if (!receiverReady || address == null) Text(stringResource(Res.string.pairing_no_address))
                    }
                }
                !receiverReady || qrAddress == null -> {
                    ConnectionNotice(AppIcons.Computer, stringResource(Res.string.pairing_no_address))
                }
                qr != null -> PairingQrCard(qr)
                else -> {
                    ConnectionNotice(AppIcons.Scan, stringResource(Res.string.pairing_qr_expired))
                    FilledTonalButton(
                        onClick = pairing::issueInvitation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(AppIcons.Retry, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.pairing_refresh))
                    }
                }
            }
            if (!pairingSucceeded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { details = !details }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(AppIcons.Link, null, Modifier.size(18.dp), tint = colors.primary)
                            Text(
                                stringResource(Res.string.pairing_details),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(if (details) AppIcons.ChevronUp else AppIcons.ChevronDown, null, Modifier.size(18.dp))
                        }
                        if (details) {
                            HorizontalDivider(color = colors.outlineVariant)
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    stringResource(Res.string.pairing_connection_settings),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it.take(40) },
                                    label = { Text(stringResource(Res.string.device_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                if (name.isNotBlank() && name.trim() != deviceName) {
                                    OutlinedButton(onClick = { onDeviceName(name.trim()) }, shape = RoundedCornerShape(12.dp)) {
                                        Text(stringResource(Res.string.save_device_name))
                                    }
                                }
                                address?.let { ConnectionDetail(stringResource(Res.string.receiver_address), it) }
                                if (fingerprint.isNotEmpty()) {
                                    ConnectionDetail(stringResource(Res.string.secure_fingerprint), fingerprint)
                                }
                                if (!paired || replacingPairing) {
                                    invitation?.let { ConnectionDetail(stringResource(Res.string.secure_invitation), it) }
                                    if (receiverReady && qrAddress == null && address != null) {
                                        TextButton(onClick = pairing::issueInvitation) {
                                            Text(stringResource(Res.string.pairing_issue_invitation))
                                        }
                                    }
                                }
                                if (paired) {
                                    HorizontalDivider(color = colors.outlineVariant)
                                    TextButton(
                                        onClick = { confirmingForget = true },
                                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                                    ) {
                                        Icon(AppIcons.Unlink, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(Res.string.pairing_revoke))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmingForget && !pairingSucceeded) {
        AppDialog(
            onDismissRequest = { confirmingForget = false },
            title = stringResource(Res.string.pairing_confirm_forget),
            icon = AppIcons.Unlink,
            confirmLabel = stringResource(Res.string.pairing_revoke),
            onConfirm = { confirmingForget = false; state.revoke() },
            dismissLabel = stringResource(Res.string.attachment_cancel),
            destructive = true,
        ) { Text(stringResource(Res.string.pairing_confirm_forget_detail)) }
    }
}

@Composable
private fun PairingQrCard(qr: ImageBitmap) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        BoxWithConstraints(Modifier.padding(20.dp)) {
            val qrImage: @Composable () -> Unit = {
                // The QR code needs an opaque white quiet zone in both light and dark themes.
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Image(qr, stringResource(Res.string.pairing_scan_title), Modifier.padding(6.dp).size(184.dp))
                }
            }
            if (maxWidth >= 360.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    qrImage()
                    PairingInstructions(Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    qrImage()
                    PairingInstructions(Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun PairingInstructions(modifier: Modifier, textAlign: TextAlign = TextAlign.Start) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.pairing_ready),
            modifier = Modifier.fillMaxWidth(),
            textAlign = textAlign,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.pairing_scan_hint),
            modifier = Modifier.fillMaxWidth(),
            textAlign = textAlign,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(Res.string.pairing_same_network),
            modifier = Modifier.fillMaxWidth(),
            textAlign = textAlign,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionStatusCard(connected: Boolean, title: String, detail: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (connected) colors.primaryContainer else colors.background,
        contentColor = if (connected) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Computer, null, Modifier.size(36.dp))
                Surface(shape = RoundedCornerShape(50), color = colors.surface) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(if (connected) AppIcons.Check else AppIcons.Link, null, Modifier.size(18.dp), tint = colors.primary)
                    }
                }
                Icon(AppIcons.Phone, null, Modifier.size(36.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConnectionNotice(icon: ImageVector, message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ConnectionDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            SelectionContainer {
                Text(value, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
