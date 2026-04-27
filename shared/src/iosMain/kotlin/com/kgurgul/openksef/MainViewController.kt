package com.kgurgul.openksef

import androidx.compose.ui.window.ComposeUIViewController
import com.kgurgul.openksef.di.appModule
import org.koin.core.context.startKoin

fun MainViewController(): platform.UIKit.UIViewController {
    startKoin {
        modules(appModule)
    }
    return ComposeUIViewController { App() }
}
