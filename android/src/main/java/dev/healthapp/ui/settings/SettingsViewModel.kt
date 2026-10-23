package dev.healthapp.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.healthapp.AppContainer
import dev.healthapp.HealthStatus
import dev.healthapp.data.Export
import dev.healthapp.data.Settings
import dev.healthapp.domain.HealthMath
import dev.healthapp.ui.trends.trackedBurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Average device-tracked burn over the last two weeks, the basis for a suggested calorie target. */
data class BurnBasis(val avgKcal: Double, val days: Int)

data class SettingsState(
    val settings: Settings = Settings(),
    val health: HealthStatus = HealthStatus(),
    val syncing: Boolean = false,
    val burn: BurnBasis? = null,
)

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    val readPermissions = app.health.readPermissions
    val historyPermission = app.health.historyPermission
    val shareFoodPermission = app.health.shareFoodPermission

    private val burn = run {
        val last = LocalDate.now().minusDays(1)
        app.db.metrics().between(last.minusDays(13).toString(), last.toString()).map { rows ->
            val tracked = rows.mapNotNull { it.trackedBurn() }
            HealthMath.meanOrNull(tracked)?.let { BurnBasis(it, tracked.size) }
        }
    }

    val state: StateFlow<SettingsState> = combine(app.settings.settings, app.sync.status, app.sync.syncing, burn) { s, h, syncing, b ->
        SettingsState(s, h, syncing, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun refreshStatus() = viewModelScope.launch { app.sync.refreshStatus() }
    fun syncNow() = viewModelScope.launch { app.sync.sync(force = true) }

    fun saveTargets(kcal: Int, protein: Int, carbs: Int, fat: Int, goalWeight: Double?) = viewModelScope.launch {
        app.settings.setTargets(kcal, protein, carbs, fat)
        app.settings.setGoalWeight(goalWeight)
        _message.value = "Targets saved"
    }

    /** Called after the permission dialog: only switch sharing on if the write permission was granted. */
    fun setShareFood(enabled: Boolean) = viewModelScope.launch {
        val status = app.sync.refreshStatus()
        val allowed = enabled && shareFoodPermission in status.granted
        app.settings.setShareFood(allowed)
        if (enabled && !allowed) _message.value = "Health Connect didn't allow writing nutrition"
    }

    fun export(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _message.value = try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { Export.writeZip(app.db, it) } ?: error("Couldn't open the file")
            }
            "Exported"
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
    }
}
