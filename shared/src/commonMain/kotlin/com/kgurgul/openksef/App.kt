package com.kgurgul.openksef

import androidx.compose.runtime.Composable
import com.kgurgul.openksef.ui.navigation.AppNavigation
import com.kgurgul.openksef.ui.theme.OpenKsefTheme

@Composable
fun App() {
    OpenKsefTheme {
        AppNavigation()
    }
}
