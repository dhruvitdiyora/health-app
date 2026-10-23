package dev.healthapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.healthapp.container
import dev.healthapp.ui.settings.SettingsScreen
import dev.healthapp.ui.today.TodayScreen
import dev.healthapp.ui.trends.TrendsScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Today),
    TRENDS("Trends", Icons.AutoMirrored.Filled.ShowChart),
    SETTINGS("Settings", Icons.Default.Settings),
}

/** App shell: three tabs, plus the Health Connect permission launcher that any tab can trigger. */
@Composable
fun HealthAppUi(sharedPhoto: android.net.Uri? = null, onSharedPhotoUsed: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }

    val permissions = rememberLauncherForActivityResult(app.health.permissionContract()) {
        scope.launch { app.sync.sync(force = true) }
    }
    val connect: () -> Unit = {
        if (app.health.isAvailable) {
            permissions.launch(app.health.readPermissions + app.health.historyPermission)
        } else {
            runCatching { context.startActivity(app.health.installIntent()) }
        }
    }

    Scaffold(
        Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (sharedPhoto != null && tab != Tab.TODAY) tab = Tab.TODAY
        when (tab) {
            Tab.TODAY -> TodayScreen(onConnectHealth = connect, contentPadding = padding,
                sharedPhoto = sharedPhoto, onSharedPhotoUsed = onSharedPhotoUsed)
            Tab.TRENDS -> TrendsScreen(contentPadding = padding)
            Tab.SETTINGS -> SettingsScreen(onConnectHealth = connect, contentPadding = padding)
        }
    }
}
