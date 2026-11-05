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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.feature.send.TransferStatus
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
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
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.connect_computer), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onClose) {
                    Icon(AppIcons.Close, contentDescription = stringResource(Res.string.close))
                }
            }
            HorizontalDivider()
            val safeDrawing = WindowInsets.safeDrawing
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .layout { measurable, constraints ->
                        // Limit the scrolling body so the sheet keeps its normal bottom anchor.
                        val clearance = safeDrawing.getTop(this) + 40.dp.roundToPx()
                        val maxHeight = minOf(480.dp.roundToPx(), constraints.maxHeight - clearance)
                            .coerceAtLeast(constraints.minHeight)
                        val body = measurable.measure(constraints.copy(maxHeight = maxHeight))
                        layout(body.width, body.height) { body.placeRelative(0, 0) }
                    }
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (remembered || paired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Computer, contentDescription = null)
                            }
                        }
                        Column {
                            Text(deviceName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    when {
                                        paired -> Res.string.device_connected
                                        busy -> Res.string.connecting
                                        else -> Res.string.device_disconnected
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                when {
                    paired -> Unit
                    !authorized -> {
                        Text(stringResource(Res.string.delivery_permission))
                        Button(onClick = onAuthorize, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.pairing_allow_network))
                        }
                    }
                    busy -> {
                        if (!remembered) Text(stringResource(Res.string.connecting))
                    }
                    else -> {
                        if (status == TransferStatus.Failed) {
                            Text(
                                stringResource(
                                    if (invitationRejected) Res.string.pairing_scan_refresh else Res.string.secure_pair_failed,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (remembered) {
                            Button(onClick = { onReconnect(null) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.retry_connection))
                            }
                            TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.scan_to_connect))
                            }
                        } else {
                            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.scan_to_connect))
                            }
                        }
                        TextButton(onClick = { manual = !manual }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.pairing_manual))
                        }
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
                            if (onDevelopmentAddress != null) {
                                TextButton(onClick = { debug = !debug }) { Text(stringResource(Res.string.development_mode)) }
                                if (debug) {
                                    var saveLaidOut by remember { mutableStateOf(false) }
                                    LaunchedEffect(saveLaidOut) {
                                        if (saveLaidOut) scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                    Text(stringResource(Res.string.development_warning))
                                    OutlinedTextField(
                                        value = debugAddress,
                                        onValueChange = { debugAddress = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = {
                                            Text(
                                                stringResource(Res.string.development_address),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        singleLine = true,
                                    )
                                    TextButton(
                                        onClick = {
                                            onDevelopmentAddress(debugAddress.trim())
                                            onClose()
                                        },
                                        modifier = Modifier.onGloballyPositioned { saveLaidOut = true },
                                    ) { Text(stringResource(Res.string.save)) }
                                }
                            }
                        }
                    }
                }
                if (remembered && !busy) {
                    TextButton(onClick = { confirmingForget = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.secure_disconnect))
                    }
                }
            }
        }
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
