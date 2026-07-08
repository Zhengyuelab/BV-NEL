package com.example.eprotocol.data.usb

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    private const val MAX_LINES = 20000
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val lines = ArrayDeque<String>()
    private val lock = Any()

    fun clear() {
        synchronized(lock) {
            lines.clear()
        }
        add("DiagnosticLog", "cleared")
    }

    fun add(tag: String, message: String) {
        val timestamp = timestampFormat.format(Date())
        synchronized(lock) {
            if (lines.size >= MAX_LINES) {
                lines.removeFirst()
            }
            lines.addLast("$timestamp $tag: $message")
        }
    }

    fun snapshot(): String {
        return synchronized(lock) {
            lines.joinToString(separator = "\n")
        }
    }
}
