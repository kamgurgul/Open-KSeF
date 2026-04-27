package com.kgurgul.openksef.data.local

actual fun dataStorePath(): String {
    val userHome = System.getProperty("user.home")
    val appDir = "$userHome/.openksef"
    val dir = java.io.File(appDir)
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return "$appDir/$DATA_STORE_FILE_NAME"
}
