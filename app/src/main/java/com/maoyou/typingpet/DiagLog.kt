package com.maoyou.typingpet

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 不具合調査用の簡易ログ。アプリ内の files/diag.log に追記する */
object DiagLog {
    private const val FILE_NAME = "diag.log"
    private const val MAX_BYTES = 200_000

    @Synchronized
    fun log(context: Context, message: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.length() > MAX_BYTES) file.delete()
            val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$time $message\n")
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): String = try {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText() else ""
    } catch (_: Throwable) {
        ""
    }
}
