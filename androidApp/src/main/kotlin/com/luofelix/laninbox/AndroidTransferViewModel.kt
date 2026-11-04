package com.luofelix.laninbox

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.OutboundDelivery
import com.luofelix.laninbox.feature.send.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FileTransferState(
    val status: TransferStatus? = null,
    val name: String? = null,
    val progress: Float? = null,
)

internal class AndroidTransferViewModel(application: Application) : AndroidViewModel(application) {
    val files = AndroidTransferFiles(application)
    private val mutableClient = MutableStateFlow<SecureLanClient?>(null)
    val client = mutableClient.asStateFlow()
    private val pairingState = MutableStateFlow<TransferStatus?>(null)
    val pairing = pairingState.asStateFlow()
    private val sent = MutableStateFlow(FileTransferState())
    val outgoing = sent.asStateFlow()
    private val received = MutableStateFlow(FileTransferState())
    val incoming = received.asStateFlow()
    private val history = MutableStateFlow<List<OutboundDelivery>>(emptyList())
    val inbox = history.asStateFlow()
    private val receptionEnabled = MutableStateFlow(true)
    private var uploadJob: Job? = null
    private var staged: StagedUpload? = null
    private var selected: Uri? = null
    private var selectedExport: AttachmentInfo? = null

    init {
        viewModelScope.launch { history.value = files.recent() }
    }

    fun pair(address: String, fingerprint: String, invitation: String) {
        if (pairingState.value == TransferStatus.Preparing) return
        pairingState.value = TransferStatus.Preparing
        viewModelScope.launch {
            try {
                mutableClient.value = SecureLanClient.pair(address, fingerprint, invitation)
                receptionEnabled.value = true
                received.value = FileTransferState()
                pairingState.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.i("LanInbox", "HTTPS pairing failed type=${error.javaClass.simpleName} cause=${error.cause?.javaClass?.simpleName}")
                pairingState.value = TransferStatus.Failed
            }
        }
    }

    fun permissionRequired() {
        pairingState.value = TransferStatus.PermissionRequired
    }

    fun disconnect() {
        val previous = mutableClient.value
        mutableClient.value = null
        previous?.close()
        cancelUpload()
        received.value = FileTransferState()
        pairingState.value = null
    }

    fun choose(uri: Uri) {
        if (uploadJob?.isActive == true) return
        selected = uri
        staged?.file?.delete()
        staged = null
        retryUpload()
    }

    fun retryUpload() {
        val connection = client.value ?: return
        val uri = selected ?: return
        if (uploadJob?.isActive == true) return
        sent.value = FileTransferState(TransferStatus.Preparing)
        uploadJob = viewModelScope.launch {
            try {
                val file = staged ?: files.stage(uri).also { staged = it }
                sent.value = FileTransferState(TransferStatus.Sending, file.info.name, 0f)
                connection.upload(file.info, file.file) { bytes ->
                    sent.update {
                        if (it.status == TransferStatus.Sending) it.copy(progress = fraction(bytes, file.info.size)) else it
                    }
                }
                sent.value = FileTransferState(TransferStatus.Delivered, file.info.name, 1f)
                file.file.delete()
                staged = null
                selected = null
            } catch (error: CancellationException) {
                sent.update { it.copy(status = TransferStatus.Canceled) }
                throw error
            } catch (_: SessionExpired) {
                expire(connection)
                sent.update { it.copy(status = TransferStatus.Failed) }
            } catch (_: Exception) {
                sent.update { it.copy(status = TransferStatus.Failed) }
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
    }

    fun pauseReception() {
        receptionEnabled.value = false
        received.update { it.copy(status = TransferStatus.Canceled) }
    }

    fun retryReception() {
        received.value = FileTransferState()
        receptionEnabled.value = true
    }

    suspend fun receiveInForeground() {
        combine(client, receptionEnabled) { connection, enabled -> connection.takeIf { enabled } }
            .collectLatest { connection ->
                if (connection != null) receiveLoop(connection)
            }
    }

    private suspend fun receiveLoop(connection: SecureLanClient) {
        try {
            while (true) {
                val delivery = connection.next()
                if (delivery != null) {
                    val attachment = delivery.attachment
                    received.value = FileTransferState(TransferStatus.Receiving, attachment?.name, 0f)
                    files.receive(connection, delivery) { bytes ->
                        if (attachment != null) received.update {
                            if (it.status == TransferStatus.Receiving) it.copy(progress = fraction(bytes, attachment.size)) else it
                        }
                    }
                    history.value = files.recent()
                    connection.acknowledge(delivery.id)
                    received.value = FileTransferState(TransferStatus.Delivered, attachment?.name, 1f)
                }
                delay(1_500)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SessionExpired) {
            expire(connection)
        } catch (_: Exception) {
            if (client.value === connection) {
                receptionEnabled.value = false
                received.update { it.copy(status = TransferStatus.Failed) }
            }
        }
    }

    private fun expire(connection: SecureLanClient) {
        if (mutableClient.compareAndSet(connection, null)) {
            connection.close()
            pairingState.value = TransferStatus.Failed
            received.update { it.copy(status = TransferStatus.Failed) }
        }
    }

    fun selectExport(attachment: AttachmentInfo) {
        selectedExport = attachment
    }

    fun completeExport(uri: Uri?) {
        val attachment = selectedExport
        selectedExport = null
        if (attachment == null || uri == null) return
        viewModelScope.launch {
            try {
                files.export(attachment, uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                received.update { it.copy(status = TransferStatus.Failed) }
            }
        }
    }

    override fun onCleared() {
        client.value?.close()
        staged?.file?.delete()
    }
}

private fun fraction(bytes: Long, size: Long): Float =
    if (size == 0L) 1f else (bytes.toDouble() / size).toFloat().coerceIn(0f, 1f)
