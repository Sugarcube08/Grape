package com.grape.mobile.database

import android.content.Context
import java.io.File

class DatabaseHelper(private val context: Context) {
    fun getDatabasePath(): String {
        val databaseFile = File(context.filesDir, "grape.sqlite")
        databaseFile.parentFile?.mkdirs()
        return databaseFile.absolutePath
    }
}
