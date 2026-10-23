package dev.healthapp

import android.content.Context
import android.util.Log
import dev.healthapp.data.SettingsStore
import dev.healthapp.data.db.AppDatabase
import dev.healthapp.data.db.FoodEntry
import dev.healthapp.data.health.HealthConnectManager
import dev.healthapp.data.health.HealthSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val db = AppDatabase.create(context)
    val settings = SettingsStore(context)
    val health = HealthConnectManager(context)
    val food = FoodRepository(db, health, settings)
    val sync = SyncController(health, HealthSync(health, db), settings)
    val calories = dev.healthapp.ml.CalorieEstimator(context)
}

/** Health Connect availability plus what the user has granted, refreshed whenever the app resumes. */
data class HealthStatus(
    val sdkStatus: Int = 0,
    val granted: Set<String> = emptySet(),
    val historyAvailable: Boolean = false,
) {
    val available: Boolean get() = sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
}

class SyncController(
    private val health: HealthConnectManager,
    private val healthSync: HealthSync,
    private val settings: SettingsStore,
) {
    private val _status = MutableStateFlow(HealthStatus())
    val status: StateFlow<HealthStatus> = _status.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val mutex = Mutex()

    suspend fun refreshStatus(): HealthStatus {
        val s = runCatching {
            HealthStatus(health.sdkStatus(), health.grantedPermissions(), health.historyFeatureAvailable())
        }.getOrElse { HealthStatus(health.sdkStatus()) }
        _status.value = s
        return s
    }

    /**
     * A full backfill at most once a day (a year with the history permission, else 30 days, the most
     * Health Connect allows without it); otherwise just the last week, which is fast.
     */
    suspend fun sync(force: Boolean = false) = mutex.withLock {
        val status = refreshStatus()
        if (!status.available || status.granted.none { it in health.readPermissions }) return@withLock
        val now = System.currentTimeMillis()
        val last = settings.current().lastSyncMillis
        if (!force && now - last < 5 * 60_000) return@withLock
        val fullBackfill = now - last > 20 * 3600_000L
        val days = when {
            !fullBackfill -> 7
            health.historyPermission in status.granted -> 365
            else -> 30
        }
        _syncing.value = true
        try {
            val result = healthSync.sync(days)
            settings.setLastSync(now, result.message)
        } catch (e: Exception) {
            Log.w("HealthSync", "sync failed", e)
            settings.setLastSync(last, "Sync failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _syncing.value = false
        }
    }
}

/** Food entries live in Room; when the user opts in, each one is mirrored to Health Connect too. */
class FoodRepository(
    private val db: AppDatabase,
    private val health: HealthConnectManager,
    private val settings: SettingsStore,
) {
    private suspend fun shouldShare(): Boolean = settings.current().shareFoodToHealthConnect &&
        runCatching { health.shareFoodPermission in health.grantedPermissions() }.getOrDefault(false)

    suspend fun add(entry: FoodEntry): Long {
        val id = db.food().insert(entry)
        if (shouldShare()) {
            runCatching { health.writeFood(entry) }
                .onSuccess { hcId -> if (hcId != null) db.food().update(entry.copy(id = id, hcRecordId = hcId)) }
                .onFailure { Log.w("FoodRepository", "share to Health Connect failed", it) }
        }
        return id
    }

    suspend fun update(entry: FoodEntry) {
        var updated = entry.copy(hcRecordId = null)
        entry.hcRecordId?.let { runCatching { health.deleteFood(it) } }
        if (shouldShare()) {
            runCatching { health.writeFood(entry) }.getOrNull()?.let { updated = updated.copy(hcRecordId = it) }
        }
        db.food().update(updated)
    }

    suspend fun delete(entry: FoodEntry) {
        db.food().delete(entry)
        entry.hcRecordId?.let { runCatching { health.deleteFood(it) } }
    }
}
