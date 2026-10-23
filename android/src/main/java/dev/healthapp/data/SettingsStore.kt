package dev.healthapp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val kcalTarget: Int = 2200,
    val proteinTarget: Int = 140,
    val carbsTarget: Int = 220,
    val fatTarget: Int = 70,
    val goalWeightKg: Double? = null,
    val shareFoodToHealthConnect: Boolean = false,
    val lastSyncMillis: Long = 0,
    val lastSyncMessage: String = "",
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val kcal = intPreferencesKey("kcal_target")
        val protein = intPreferencesKey("protein_target")
        val carbs = intPreferencesKey("carbs_target")
        val fat = intPreferencesKey("fat_target")
        val goalWeight = doublePreferencesKey("goal_weight_kg")
        val shareFood = booleanPreferencesKey("share_food_hc")
        val lastSync = longPreferencesKey("last_sync")
        val lastSyncMessage = stringPreferencesKey("last_sync_message")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setTargets(kcal: Int, protein: Int, carbs: Int, fat: Int) = context.dataStore.edit {
        it[Keys.kcal] = kcal
        it[Keys.protein] = protein
        it[Keys.carbs] = carbs
        it[Keys.fat] = fat
    }

    suspend fun setGoalWeight(kg: Double?) = context.dataStore.edit {
        if (kg == null) it.remove(Keys.goalWeight) else it[Keys.goalWeight] = kg
    }

    suspend fun setShareFood(enabled: Boolean) = context.dataStore.edit { it[Keys.shareFood] = enabled }

    suspend fun setLastSync(millis: Long, message: String) = context.dataStore.edit {
        it[Keys.lastSync] = millis
        it[Keys.lastSyncMessage] = message
    }

    private fun Preferences.toSettings() = Settings(
        kcalTarget = this[Keys.kcal] ?: 2200,
        proteinTarget = this[Keys.protein] ?: 140,
        carbsTarget = this[Keys.carbs] ?: 220,
        fatTarget = this[Keys.fat] ?: 70,
        goalWeightKg = this[Keys.goalWeight],
        shareFoodToHealthConnect = this[Keys.shareFood] ?: false,
        lastSyncMillis = this[Keys.lastSync] ?: 0,
        lastSyncMessage = this[Keys.lastSyncMessage] ?: "",
    )
}
