package com.luofelix.laninbox

import java.util.prefs.Preferences

internal class DesktopSettings {
    private val preferences = Preferences.userRoot().node("com/luofelix/laninbox")

    fun deviceName(): String = normalize(preferences.get("device_name", System.getenv("COMPUTERNAME") ?: "LanInbox"))

    fun saveDeviceName(name: String): String = normalize(name).also { preferences.put("device_name", it) }

    private fun normalize(name: String): String {
        var result = name.filterNot { it.isISOControl() }.trim().take(40)
        while (result.encodeToByteArray().size > 48) result = result.dropLast(1)
        return result.ifBlank { "LanInbox" }
    }
}
