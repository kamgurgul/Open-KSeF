package com.kgurgul.openksef

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kgurgul.openksef.di.appModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

fun main() {
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            modules(appModule)
        }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Open KSeF",
        ) {
            App()
        }
    }
}
