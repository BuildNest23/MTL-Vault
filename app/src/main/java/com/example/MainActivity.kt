package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.MtlVaultNavGraph
import com.example.ui.theme.MtlVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MtlVaultApplication
        setContent {
            MtlVaultTheme {
                MtlVaultNavGraph(application = app)
            }
        }
    }
}
