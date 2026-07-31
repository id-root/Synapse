package com.synapse.lantransfer.data.model

import org.json.JSONObject

/**
 * Wire-format file header matching the Go backend's FileHeader struct.
 * Sent as JSON over the TLS connection before file data.
 *
 * Protocol flow:
 *   Sender → [8-byte headerLen][JSON FileHeader]
 *   Receiver → [8-byte reqLen][JSON TransferRequest]
 *   Sender → [raw file bytes][32-byte checksum]
 */
data class FileHeader(
    val name: String,
    val size: Long,
    val isArchive: Boolean = false,
    val compression: String = COMPRESSION_NONE
) {
    fun toJson(): ByteArray {
        val json = JSONObject().apply {
            put("name", name)
            put("size", size)
            if (isArchive) put("is_archive", true)
            if (compression != COMPRESSION_NONE) put("compression", compression)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val COMPRESSION_NONE = "none"
        const val COMPRESSION_GZIP = "gzip"
        const val COMPRESSION_ZSTD = "zstd"
        const val COMPRESSION_CHUNKED = "chunked"

        fun fromJson(bytes: ByteArray): FileHeader {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            return FileHeader(
                name = json.getString("name"),
                size = json.getLong("size"),
                isArchive = json.optBoolean("is_archive", false),
                compression = json.optString("compression", COMPRESSION_NONE)
            )
        }
    }
}

/**
 * Wire-format transfer request matching the Go backend's TransferRequest struct.
 * Sent by the receiver to negotiate resume offset.
 */
data class TransferRequest(
    val offset: Long = 0,
    val peerName: String = ""
) {
    fun toJson(): ByteArray {
        val json = JSONObject().apply {
            put("offset", offset)
            put("peer_name", peerName)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJson(bytes: ByteArray): TransferRequest {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            return TransferRequest(
                offset = json.optLong("offset", 0),
                peerName = json.optString("peer_name", "")
            )
        }
    }
}
