package com.eread.companion

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EreadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { File(filesDir, "capture_last.png").delete() }
        runCatching { BookState.migrate(this) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val f = File(filesDir, "crash.log")
                val head = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                f.appendText("\n===== " + head + " =====\n" + Log.getStackTraceString(error))
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
