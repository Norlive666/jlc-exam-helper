package com.ai.assistance.operit.util

import android.util.Log

// Android log adapter; the full app's chat, package and file log services are omitted.
object AppLogger {
    fun d(tag: String, message: String) { Log.d(tag, message) }
    fun w(tag: String, message: String) { Log.w(tag, message) }
    fun w(tag: String, message: String, error: Throwable) { Log.w(tag, message, error) }
    fun e(tag: String, message: String) { Log.e(tag, message) }
    fun e(tag: String, message: String, error: Throwable) { Log.e(tag, message, error) }
}
