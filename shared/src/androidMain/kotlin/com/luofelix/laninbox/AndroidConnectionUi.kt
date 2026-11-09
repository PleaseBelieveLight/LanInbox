package com.luofelix.laninbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.feature.send.TransferStatus
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AndroidConnectionSheet(
    deviceName: String,
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
    LanInboxTheme {
        val scrollState = rememberScrollState()
        AndroidAppSheet(
            title = stringResource(Res.string.connect_computer),
            icon = AppIcons.Computer,
            subtitle = stringResource(Res.string.pairing_same_network),
            onClose = onClose,
            scrollState = scrollState,
        ) {
            if (remembered || paired) {
                AndroidConnectionDeviceCard(deviceName, paired, busy)
            } else {
                AppDialogPanel(emphasized = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Scan, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(Res.string.pairing_scan_computer_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            when {
                paired -> Unit
                !authorized -> {
                    AppDialogPanel {
                        Text(stringResource(Res.string.delivery_permission), style = MaterialTheme.typography.bodyMedium)
                    }
                    AppDialogButton(
                        stringResource(Res.string.pairing_allow_network), onAuthorize,
                        Modifier.fillMaxWidth(), icon = AppIcons.Link,
                    )
                }
                busy -> {
                    if (!remembered) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(Res.string.connecting), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> {
                    if (status == TransferStatus.Failed) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                stringResource(if (invitationRejected) Res.string.pairing_scan_refresh else Res.string.secure_pair_failed),
                                modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    if (remembered) {
                        AppDialogButton(
                            stringResource(Res.string.retry_connection), { onReconnect(null) },
                            Modifier.fillMaxWidth(), icon = AppIcons.Retry,
                        )
                    }
                    AppDialogButton(
                        stringResource(Res.string.scan_to_connect), onScan,
                        Modifier.fillMaxWidth(), icon = AppIcons.Scan, secondary = remembered,
                    )
                    AppDialogDisclosure(
                        title = stringResource(Res.string.pairing_manual),
                        icon = AppIcons.Link,
                        expanded = manual,
                        onToggle = { manual = !manual },
                    ) {
                        AndroidPairingField(
                            address, { address = it }, stringResource(Res.string.pairing_address),
                            keyboardType = KeyboardType.Uri,
                            supportingText = stringResource(Res.string.receiver_address),
                        )
                        if (remembered && address.isNotBlank()) {
                            AppDialogButton(
                                stringResource(Res.string.reconnect_desktop), { onReconnect(address) },
                                Modifier.fillMaxWidth(), icon = AppIcons.Retry, secondary = true,
                            )
                        }
                        AndroidPairingField(
                            fingerprint, { fingerprint = it }, stringResource(Res.string.pairing_fingerprint),
                        )
                        AndroidPairingField(
                            invitation, { invitation = it }, stringResource(Res.string.secure_invitation), secret = true,
                        )
                        AppDialogButton(
                            stringResource(Res.string.secure_pair), { onPair(address, fingerprint, invitation) },
                            Modifier.fillMaxWidth(), icon = AppIcons.Link,
                            enabled = address.isNotBlank() && fingerprint.isNotBlank() && invitation.isNotBlank(),
                        )
                        if (onDevelopmentAddress != null) {
                            AppDialogDisclosure(
                                title = stringResource(Res.string.development_mode),
                                icon = AppIcons.Code,
                                expanded = debug,
                                onToggle = { debug = !debug },
                            ) {
                                var saveLaidOut by remember { mutableStateOf(false) }
                                LaunchedEffect(saveLaidOut) {
                                    if (saveLaidOut) scrollState.animateScrollTo(scrollState.maxValue)
                                }
                                Text(
                                    stringResource(Res.string.development_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AndroidPairingField(
                                    debugAddress, { debugAddress = it }, stringResource(Res.string.development_address),
                                    keyboardType = KeyboardType.Uri,
                                )
                                AppDialogButton(
                                    stringResource(Res.string.save),
                                    onClick = {
                                        onDevelopmentAddress(debugAddress.trim())
                                        onClose()
                                    },
                                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { saveLaidOut = true },
                                )
                            }
                        }
                    }
                }
            }
            if (remembered && !busy) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AppDialogButton(
                    stringResource(Res.string.secure_disconnect), { confirmingForget = true },
                    Modifier.fillMaxWidth(), icon = AppIcons.Unlink, secondary = true, destructive = true,
                )
            }
        }
        if (confirmingForget) {
            AppDialog(
                onDismissRequest = { confirmingForget = false },
                title = stringResource(Res.string.pairing_confirm_forget),
                icon = AppIcons.Unlink,
                confirmLabel = stringResource(Res.string.secure_disconnect),
                onConfirm = { confirmingForget = false; onForget() },
                dismissLabel = stringResource(Res.string.attachment_cancel),
                destructive = true,
            ) { Text(stringResource(Res.string.pairing_confirm_forget_detail)) }
        }
    }
}

@Composable
private fun AndroidConnectionDeviceCard(deviceName: String, paired: Boolean, busy: Boolean) {
    val colors = MaterialTheme.colorScheme
    AppDialogPanel(emphasized = paired) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Computer, null, Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(deviceName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Icon(if (paired) AppIcons.Check else AppIcons.Link, null, Modifier.size(14.dp), tint = colors.primary)
                    Text(
                        stringResource(when {
                            paired -> Res.string.device_connected
                            busy -> Res.string.connecting
                            else -> Res.string.device_disconnected
                        }),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (paired) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (paired) Text(stringResource(Res.string.connected_computer_detail), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AndroidPairingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    secret: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText = supportingText?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@Composable
fun AndroidPairingScanScreen(
    instruction: String,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    busy: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    preview: (@Composable (Modifier) -> Unit)? = null,
) {
    LanInboxTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            topBar = {
                AndroidTopBar(stringResource(Res.string.pairing_scan_title), onBack = onBack) {
                    IconButton(onClick = onDetails) {
                        Icon(AppIcons.More, contentDescription = stringResource(Res.string.pairing_details))
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .padding(horizontal = 18.dp),
            ) {
                val compact = maxHeight < 360.dp
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = if (compact) 16.dp else 28.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            instruction,
                            Modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (actionLabel != null) {
                            TextButton(onClick = onAction) { Text(actionLabel) }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Surface(
                            modifier = Modifier.widthIn(max = 420.dp).aspectRatio(1f),
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF172333),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (preview != null) {
                                    preview(Modifier.matchParentSize())
                                } else {
                                    Icon(AppIcons.Scan, contentDescription = null, modifier = Modifier.size(44.dp), tint = Color(0xFFB8C8E0))
                                }
                                if (busy) {
                                    Surface(shape = MaterialTheme.shapes.medium) {
                                        CircularProgressIndicator(Modifier.padding(12.dp).size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
