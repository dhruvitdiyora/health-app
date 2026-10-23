package dev.healthapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.healthapp.ui.theme.HealthTheme

/** Shown by Health Connect when the user asks why this app wants health data. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Privacy", style = MaterialTheme.typography.headlineSmall)
                        PRIVACY_TEXT.split("\n\n").forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

const val PRIVACY_TEXT = """This app is a personal, local-first health log. It has no account and no server.

It reads steps, calories burned, sleep, resting heart rate, heart rate variability, weight and workouts from Health Connect, so it can show them beside the food you log. That data is copied into a database on this phone only.

If you turn on "Share food with Health Connect", the food you log is written to Health Connect as nutrition records, so other apps you allow (for example WHOOP) can use it.

Nothing is uploaded anywhere. You can export everything as CSV from Settings, and uninstalling the app deletes its database."""
