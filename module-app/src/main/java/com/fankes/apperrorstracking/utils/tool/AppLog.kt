package com.fankes.apperrorstracking.utils.tool

import android.util.Log
import java.io.Serializable

/** Serializable DTO; stack traces cross Binder as plain text, never Throwable objects. */
data class AppLogData(val priority: String, val msg: String, val timestamp: Long, val stackTrace: String? = null) : Serializable {
    override fun toString() = "$timestamp $priority: $msg"
}
object AppLog {
    private val entries = ArrayDeque<AppLogData>()
    private const val LIMIT = 1000
    private const val TAG = "AppErrorsTracking"
    private fun write(priority: String, msg: String, e: Throwable?) {
        val trace = e?.let(Log::getStackTraceString)
        synchronized(entries) {
            if (entries.size >= LIMIT) entries.removeFirst()
            entries.addLast(AppLogData(priority, msg, System.currentTimeMillis(), trace))
        }
        Log.println(when (priority) { "D" -> Log.DEBUG; "I" -> Log.INFO; "W" -> Log.WARN; else -> Log.ERROR }, TAG, msg + (trace?.let { "\n$it" } ?: ""))
    }
    fun debug(msg: String, e: Throwable? = null) = write("D", msg, e)
    fun info(msg: String, e: Throwable? = null) = write("I", msg, e)
    fun warn(msg: String, e: Throwable? = null) = write("W", msg, e)
    fun error(msg: String, e: Throwable? = null) = write("E", msg, e)
    fun snapshot(): ArrayList<AppLogData> = synchronized(entries) { ArrayList(entries) }
    fun contents(data: List<AppLogData>) = data.joinToString("\n") { it.toString() + (it.stackTrace?.let { s -> "\n$s" } ?: "") }
}