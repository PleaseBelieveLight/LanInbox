package com.luofelix.laninbox

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.DevelopmentMessageSender
import com.luofelix.laninbox.feature.send.MessageSender
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val logger = AppLogger { event -> Log.i("LanInbox", event) }
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    private var developmentSender: DevelopmentMessageSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            logger.info("Android activity started")
        }

        val developmentMode = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val sender = if (developmentMode) {
            val client = DevelopmentMessageSender()
            developmentSender = client
            MessageSender { address, message ->
                if (Build.VERSION.SDK_INT >= 37 &&
                    checkSelfPermission(LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
                ) {
                    localNetworkPermission.launch(LOCAL_NETWORK)
                    DeliveryResult.PermissionRequired
                } else {
                    client.send(address, message)
                }
            }
        } else {
            null
        }
        val senderId = if (sender == null) "" else loadSenderId()
        val initialDraft = if (developmentMode && intent.action == Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        } else {
            ""
        }
        setContent {
            App(sender = sender, senderId = senderId, initialDraft = initialDraft)
        }
    }

    override fun onDestroy() {
        developmentSender?.close()
        super.onDestroy()
    }

    private fun loadSenderId(): String {
        val settings = getSharedPreferences("laninbox_development", MODE_PRIVATE)
        return settings.getString("sender_id", null) ?: UUID.randomUUID().toString().also {
            settings.edit().putString("sender_id", it).apply()
        }
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
