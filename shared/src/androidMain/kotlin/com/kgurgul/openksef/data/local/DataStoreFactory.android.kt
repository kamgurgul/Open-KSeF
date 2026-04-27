package com.kgurgul.openksef.data.local

actual fun dataStorePath(): String {
    // On Android, this will be set by the Koin module using the application context
    return AndroidDataStorePath.path
}

object AndroidDataStorePath {
    lateinit var path: String

    fun init(filesDir: String) {
        path = "$filesDir/$DATA_STORE_FILE_NAME"
    }
}
