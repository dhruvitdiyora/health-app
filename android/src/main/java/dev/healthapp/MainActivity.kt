package dev.healthapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import dev.healthapp.data.ThemeMode
import dev.healthapp.ui.HealthAppUi
import dev.healthapp.ui.theme.HealthTheme

class MainActivity : ComponentActivity() {
    /** A photo shared to the app ("Share to Health"), waiting to be opened in Log food. */
    private val sharedPhoto = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedPhoto.value = photoFrom(intent)
        // Shares from a share sheet start a fresh instance (onCreate above, tested on the Pixel 9). In case
        // one is delivered to a running instance instead; note `adb am start` deliveries never reached
        // this listener or an onNewIntent override on Android 17, so that path is unverified.
        addOnNewIntentListener { photoFrom(it)?.let { uri -> sharedPhoto.value = uri } }
        setContent {
            // The in-app theme choice (Settings) overrides the phone's dark mode; status-bar icons follow it.
            val theme by container.settings.settings.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val dark = when (theme?.theme) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; else -> systemDark }
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            HealthTheme(dark = dark) {
                HealthAppUi(sharedPhoto = sharedPhoto.value, onSharedPhotoUsed = { sharedPhoto.value = null })
            }
        }
    }

    private fun photoFrom(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
