package com.luofelix.laninbox

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.DEVELOPMENT_PORT
import com.luofelix.laninbox.feature.inbox.ReceiverStatus
import com.luofelix.laninbox.feature.send.TransferControls
import com.luofelix.laninbox.feature.send.TransferStatus
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

private val logger = AppLogger { event -> println("[${event.level.name.uppercase()}] LanInbox: ${event.render()}") }

fun main() = application {
    // Require explicit opt-in before exposing an unauthenticated cleartext listener on the LAN.
    val enabled = System.getenv("LANINBOX_DEV_RECEIVER") == "1"
    val identityPath = System.getenv("LANINBOX_TLS_KEYSTORE")
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val autoSecure = windows && !enabled && identityPath == null
    val secure = autoSecure || identityPath != null
    val bindAddress = System.getenv("LANINBOX_TLS_BIND") ?: if (autoSecure) "0.0.0.0" else "127.0.0.1"
    val dataDirectory = remember {
        val directory = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        Path.of(directory, "LanInbox")
    }
    val inbox = remember { DesktopInbox(dataDirectory.resolve("development-inbox.sqlite")) }
    val messages by inbox.messages.collectAsState()
    val protectedFiles = remember { DesktopProtectedFiles(dataDirectory.resolve("security")) }
    val pairing = remember {
        DesktopPairingSession(store = if (autoSecure) WindowsPairingVerifierStore(protectedFiles) else null)
    }
    val settings = remember { DesktopSettings() }
    var deviceName by remember { mutableStateOf(settings.deviceName()) }
    var discoveredAddress by remember { mutableStateOf<String?>(null) }
    val attachmentStore = remember { DesktopAttachmentStore(dataDirectory.resolve("development-attachments")) }
    val attachments by attachmentStore.attachments.collectAsState()
    val outbox = remember { DesktopOutbox() }
    var fingerprint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(ReceiverStatus.Disabled) }

    DisposableEffect(Unit) {
        logger.info("Desktop application started")
        onDispose { logger.info("Desktop application stopped") }
    }
    LaunchedEffect(outbox, pairing) {
        combine(pairing.activeSession, outbox.delivery) { session, delivery -> session to delivery }
            .collect { (session, delivery) ->
                if (delivery != null && delivery.owner != session) outbox.cancel()
            }
    }
    LaunchedEffect(status, deviceName, fingerprint) {
        if (status == ReceiverStatus.SecureListening) {
            advertiseDesktop(bindAddress, deviceName, fingerprint, logger) { discoveredAddress = it }
        }
    }
    LaunchedEffect(inbox) {
        if (!enabled && !secure) return@LaunchedEffect
        status = ReceiverStatus.Starting
        try {
            check(!enabled || identityPath == null) { "Select one receiver mode" }
            val server = if (secure) {
                inbox.initialize()
                attachmentStore.initialize()
                val (secureServer, pin) = withContext(Dispatchers.IO) {
                    pairing.restore()
                    val identity = if (identityPath == null) {
                        provisionDesktopIdentity(protectedFiles)
                    } else {
                        val password = System.getenv("LANINBOX_TLS_PASSWORD")
                        require(!password.isNullOrEmpty()) { "The identity keystore requires a password" }
                        loadDesktopIdentity(Path.of(identityPath), password)
                    }
                    var pin = ""
                    val secureServer = desktopIdentityServer(
                        identity,
                        attachmentStore,
                        pairing = pairing,
                        outbox = outbox,
                        bindAddress = bindAddress,
                        inbox = inbox,
                        logger = logger,
                        onFingerprint = { pin = it },
                    )
                    secureServer to pin
                }
                fingerprint = pin
                secureServer
            } else {
                inbox.initialize()
                embeddedServer(Netty, applicationEnvironment(), configure = {
                    connector {
                        host = "0.0.0.0"
                        port = DEVELOPMENT_PORT
                    }
                    requestReadTimeoutSeconds = 10
                }) {
                    messageRoutes(inbox, logger)
                }
            }
            try {
                withContext(Dispatchers.IO) { server.start(wait = false) }
                if (!secure) {
                    status = ReceiverStatus.Listening
                    logger.info("Development receiver listening", "port" to DEVELOPMENT_PORT)
                } else {
                    status = if (bindAddress == "127.0.0.1") ReceiverStatus.IdentityListening else ReceiverStatus.SecureListening
                    logger.info("HTTPS receiver listening")
                }
                awaitCancellation()
            } finally {
                // The application effect is canceled on exit. Let Netty finish shutting down.
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        server.stop(500, 1_500)
                    } finally {
                        outbox.cancel()
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            status = ReceiverStatus.Failed
            logger.warn("Receiver failed to start", "type" to error.javaClass.simpleName)
        }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "LanInbox",
    ) {
        val scope = rememberCoroutineScope()
        val transferState = remember(scope, pairing, outbox) { DesktopTransferState(scope, pairing, outbox) }
        var exportFailed by remember { mutableStateOf(false) }
        fun changeHistory(change: suspend () -> Unit) {
            scope.launch {
                exportFailed = try {
                    change()
                    false
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    true
                }
            }
        }
        App(
            messages = messages,
            receiverStatus = status,
            inboxOnly = true,
            attachments = attachments,
            previewAttachment = { desktopPreview(attachmentStore.payload(it)) },
            deleteMessage = { changeHistory { inbox.delete(it.request) } },
            deleteAttachment = { changeHistory { attachmentStore.delete(it) } },
            clearHistory = { changeHistory { inbox.delete(null); attachmentStore.delete(null) } },
            exportAttachment = { attachment ->
                chooseDesktopPath(window, FileDialog.SAVE, attachment.name)?.let { destination ->
                    scope.launch {
                        exportFailed = try {
                            withContext(Dispatchers.IO) {
                                Files.copy(attachmentStore.payload(attachment), destination, REPLACE_EXISTING)
                            }
                            false
                        } catch (_: IOException) {
                            true
                        }
                    }
                }
            },
            inboxActions = {
                DesktopTransferActions(
                    window = window,
                    receiverReady = status == ReceiverStatus.IdentityListening || status == ReceiverStatus.SecureListening,
                    host = discoveredAddress ?: bindAddress.takeUnless { it == "0.0.0.0" },
                    fingerprint = fingerprint,
                    pairing = pairing,
                    outbox = outbox,
                    state = transferState,
                    deviceName = deviceName,
                ) { name ->
                    deviceName = settings.saveDeviceName(name)
                }
                if (exportFailed) TransferControls(TransferStatus.Failed)
            },
        )
    }
}
