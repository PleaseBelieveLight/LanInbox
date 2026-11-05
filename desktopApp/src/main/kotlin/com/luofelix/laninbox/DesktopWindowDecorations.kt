package com.luofelix.laninbox

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luofelix.laninbox.core.logging.AppLogger
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

/** Matches the native Windows caption to the app while retaining system window controls. */
@Composable
internal fun DesktopWindowDecorations(window: ComposeWindow, logger: AppLogger) {
    if (!Platform.isWindows()) return
    val darkTheme = isSystemInDarkTheme()
    val colors = lanInboxColorScheme(darkTheme)
    val captionColor = colors.surface.toWindowsColor()
    val textColor = colors.onSurface.toWindowsColor()
    val appName = stringResource(Res.string.app_name)
    DisposableEffect(window, darkTheme, captionColor, textColor, appName) {
        // Keep a screen-reader name even though the visible caption contains only the app icon.
        window.accessibleContext.accessibleName = appName
        fun updateCaption() {
            if (!window.isDisplayable) return
            try {
                val handle = HWND(Native.getWindowPointer(window))
                val attributes = listOf(
                    20 to if (darkTheme) 1 else 0, // DWMWA_USE_IMMERSIVE_DARK_MODE
                    35 to captionColor, // DWMWA_CAPTION_COLOR
                    36 to textColor, // DWMWA_TEXT_COLOR
                )
                for ((attribute, value) in attributes) {
                    val result = DwmApi.instance.DwmSetWindowAttribute(handle, attribute, IntByReference(value), 4)
                    if (result.toInt() < 0) {
                        // Older Windows versions do not implement every caption attribute.
                        logger.info(
                            "Window decoration attribute unavailable",
                            "attribute" to attribute,
                            "hresult" to result.toInt().toUInt().toString(16),
                        )
                    }
                }
            } catch (error: LinkageError) {
                logger.warn("Window decoration unavailable", "reason" to error.javaClass.simpleName)
            } catch (error: RuntimeException) {
                logger.warn("Window decoration failed", "reason" to error.javaClass.simpleName)
            }
        }
        // Initial composition can precede AWT's native peer creation.
        val listener = object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) = updateCaption()
        }
        window.addWindowListener(listener)
        updateCaption()
        onDispose { window.removeWindowListener(listener) }
    }
}

private fun Color.toWindowsColor(): Int {
    val argb = toArgb()
    // DWM takes COLORREF (0x00BBGGRR), whereas Compose exposes ARGB.
    return ((argb ushr 16) and 0xFF) or (argb and 0xFF00) or ((argb and 0xFF) shl 16)
}

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(window: HWND, attribute: Int, value: IntByReference, size: Int): HRESULT

    companion object {
        val instance: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }
    }
}
