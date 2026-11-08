package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.ui.navigation.MtlVaultNavGraph
import com.example.ui.theme.MtlVaultTheme

import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {

    private val currentIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntentState.value = intent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        if (com.example.logging.AppLogger.consumeCrashFlag(this)) {
            android.widget.Toast.makeText(
                this,
                "The app closed unexpectedly last time. You can share the log in Settings > Diagnostics.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        val app = application as MtlVaultApplication
        setContent {
            val dynamicColorFlow = remember { app.settingsDataStore.dynamicColorFlow }
            // null until the saved value is read (a few milliseconds). Nothing is drawn before that,
            // so there is no colour flash on launch and the main thread is never blocked.
            val dynamicColor by dynamicColorFlow.collectAsState<Boolean?>(initial = null)
            val resolvedDynamicColor = dynamicColor
            if (resolvedDynamicColor != null) {
                MtlVaultTheme(dynamicColor = resolvedDynamicColor) {
                    MtlVaultNavGraph(
                        application = app,
                        intent = currentIntentState.value,
                        onIntentConsumed = {
                            // Drop the flag so rotation / recreation doesn't re-open the reader
                            intent?.removeExtra("OPEN_READER")
                            currentIntentState.value = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }
}
