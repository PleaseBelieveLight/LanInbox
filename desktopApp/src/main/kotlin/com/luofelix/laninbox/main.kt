package com.luofelix.laninbox

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.DEVELOPMENT_PORT
import com.luofelix.laninbox.feature.inbox.ReceiverStatus
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

private val logger = AppLogger { event -> println("[INFO] LanInbox: $event") }

fun main() = application {
    val enabled = System.getenv("LANINBOX_DEV_RECEIVER") == "1"
    val inbox = remember {
        val directory = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        DesktopInbox(Path.of(directory, "LanInbox", "development-inbox.sqlite"))
    }
    val messages by inbox.messages.collectAsState()
    var status by remember { mutableStateOf(ReceiverStatus.Disabled) }

    DisposableEffect(Unit) {
        logger.info("Desktop application started")
        onDispose { logger.info("Desktop application stopped") }
    }
    LaunchedEffect(inbox) {
        if (!enabled) return@LaunchedEffect
        status = ReceiverStatus.Starting
        try {
            inbox.initialize()
            val server = embeddedServer(Netty, host = "0.0.0.0", port = DEVELOPMENT_PORT, configure = {
                requestReadTimeoutSeconds = 10
            }) {
                messageRoutes(inbox, logger)
            }
            try {
                withContext(Dispatchers.IO) { server.start(wait = false) }
                status = ReceiverStatus.Listening
                logger.info("Development receiver listening port=$DEVELOPMENT_PORT")
                awaitCancellation()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { server.stop(500, 1_500) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            status = ReceiverStatus.Failed
            logger.info("Development receiver failed to start")
        }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "LanInbox",
    ) {
        App(messages = messages, receiverStatus = status, inboxOnly = true)
    }
}
