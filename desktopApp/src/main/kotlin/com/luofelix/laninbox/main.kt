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
import kotlinx.coroutines.withContext

private val logger = AppLogger { event -> println("[INFO] LanInbox: $event") }

fun main() = application {
    // Require explicit opt-in before exposing an unauthenticated cleartext listener on the LAN.
    val enabled = System.getenv("LANINBOX_DEV_RECEIVER") == "1"
    val identityPath = System.getenv("LANINBOX_TLS_KEYSTORE")
    val bindAddress = System.getenv("LANINBOX_TLS_BIND") ?: "127.0.0.1"
    val dataDirectory = remember {
        val directory = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        Path.of(directory, "LanInbox")
    }
    val inbox = remember { DesktopInbox(dataDirectory.resolve("development-inbox.sqlite")) }
    val messages by inbox.messages.collectAsState()
    val pairing = remember { DesktopPairingSession() }
    val attachmentStore = remember { DesktopAttachmentStore(dataDirectory.resolve("development-attachments")) }
    val attachments by attachmentStore.attachments.collectAsState()
    val outbox = remember { DesktopOutbox() }
    var fingerprint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(ReceiverStatus.Disabled) }

    DisposableEffect(Unit) {
        logger.info("Desktop application started")
        onDispose { logger.info("Desktop application stopped") }
    }
    LaunchedEffect(inbox) {
        if (!enabled && identityPath == null) return@LaunchedEffect
        status = ReceiverStatus.Starting
        try {
            check(!enabled || identityPath == null) { "Select one receiver mode" }
            val server = if (identityPath != null) {
                inbox.initialize()
                attachmentStore.initialize()
                val (secureServer, pin) = withContext(Dispatchers.IO) {
                    val password = System.getenv("LANINBOX_TLS_PASSWORD")
                    require(!password.isNullOrEmpty()) { "The identity keystore requires a password" }
                    var pin = ""
                    val secureServer = desktopIdentityServer(
                        Path.of(identityPath),
                        password,
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
                if (identityPath == null) {
                    status = ReceiverStatus.Listening
                    logger.info("Development receiver listening port=$DEVELOPMENT_PORT")
                } else {
                    status = if (bindAddress == "127.0.0.1") ReceiverStatus.IdentityListening else ReceiverStatus.SecureListening
                    logger.info("HTTPS receiver listening")
                }
                awaitCancellation()
            } finally {
                // The application effect is canceled on exit. Let Netty finish shutting down.
                withContext(NonCancellable + Dispatchers.IO) { server.stop(500, 1_500) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            status = ReceiverStatus.Failed
            logger.info("Receiver failed to start")
        }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "LanInbox",
    ) {
        val scope = rememberCoroutineScope()
        var exportFailed by remember { mutableStateOf(false) }
        App(
            messages = messages,
            receiverStatus = status,
            inboxOnly = true,
            attachments = attachments,
            previewAttachment = { desktopPreview(attachmentStore.payload(it)) },
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
                if (status == ReceiverStatus.IdentityListening || status == ReceiverStatus.SecureListening) {
                    DesktopTransferActions(window, bindAddress, fingerprint, pairing, outbox)
                }
                if (exportFailed) TransferControls(TransferStatus.Failed)
            },
        )
    }
}
