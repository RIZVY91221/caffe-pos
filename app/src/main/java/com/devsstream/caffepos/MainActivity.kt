package com.devsstream.caffepos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.devsstream.caffepos.ui.nav.AppNavHost
import com.devsstream.caffepos.ui.theme.CaffePOSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaffePOSTheme {
                AppNavHost()
            }
        }
    }
}
