package com.luofelix.laninbox

import android.content.Context
import android.util.Log
import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.logging.LogLevel
import java.util.UUID

internal val androidLogger = AppLogger { event ->
    when (event.level) {
        LogLevel.Info -> Log.i("LanInbox", event.render())
        LogLevel.Warning -> Log.w("LanInbox", event.render())
    }
}

internal class AndroidSettings(context: Context) {
    private val preferences = context.getSharedPreferences("laninbox_development", Context.MODE_PRIVATE)

    /** Keeps a stable sender ID for the receiver's deduplication key. */
    fun senderId(): String = preferences.getString("sender_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("sender_id", it).apply()
    }
}
