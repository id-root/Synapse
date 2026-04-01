package com.synapse.lantransfer.util

import java.text.SimpleDateFormat
import java.util.*

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val idx = digitGroups.coerceIn(0, units.size - 1)
    return "%.1f %s".format(bytes / Math.pow(1024.0, idx.toDouble()), units[idx])
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatSpeed(bytesPerSecond: Float): String {
    return "${formatBytes(bytesPerSecond.toLong())}/s"
}
