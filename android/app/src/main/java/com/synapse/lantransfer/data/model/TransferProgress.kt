package com.synapse.lantransfer.data.model

/**
 * Real-time progress information for an active file transfer.
 */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val fileName: String,
    val speed: Float = 0f,  // bytes per second
    val peerAddress: String = ""
) {
    /** Progress as a fraction from 0.0 to 1.0 */
    val fraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    /** Progress as a percentage integer 0–100 */
    val percent: Int
        get() = (fraction * 100).toInt()
}

/**
 * Represents a file selected by the user for sending.
 */
data class SelectedFile(
    val name: String,
    val size: Long,
    val uri: String = "",
    val mimeType: String = ""
)

/**
 * Represents the overall state of the transfer engine.
 */
sealed class TransferState {
    data object Idle : TransferState()
    data class Sending(val port: Int, val progress: TransferProgress? = null) : TransferState()
    data class Receiving(val peerAddress: String, val progress: TransferProgress? = null) : TransferState()
    data class Completed(val fileName: String) : TransferState()
    data class Error(val message: String) : TransferState()
}
