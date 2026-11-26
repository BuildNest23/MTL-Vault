package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.ui.navigation.MtlVaultNavGraph
import com.example.ui.theme.AppAppearance
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MtlVaultTheme

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
            val appearanceFlow = remember { app.settingsDataStore.appAppearanceFlow }
            // null until the saved value is read (a few milliseconds). Nothing is drawn before that,
            // so there is no colour or font flash on launch and the main thread is never blocked.
            val appearance: AppAppearance? by appearanceFlow.collectAsState(initial = null)
            val resolved = appearance
            if (resolved != null) {
                val systemDark = isSystemInDarkTheme()
                val isDark = when (resolved.themeMode) {
                    AppThemeMode.SYSTEM -> systemDark
                    AppThemeMode.DARK -> true
                    AppThemeMode.LIGHT -> false
                }
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
                MtlVaultTheme(
                    darkTheme = isDark,
                    mattePalette = resolved.mattePalette,
                    fontPreset = resolved.fontPreset,
                    dynamicColor = resolved.dynamicColor
                ) {
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
