package com.luofelix.laninbox

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.luofelix.laninbox.core.logging.AppLogger

private val logger = AppLogger { event -> println("[INFO] LanInbox: $event") }

fun main() = application {
    DisposableEffect(Unit) {
        logger.info("Desktop application started")
        onDispose { logger.info("Desktop application stopped") }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "LanInbox",
    ) {
        App()
    }
}
