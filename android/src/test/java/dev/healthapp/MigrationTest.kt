package dev.healthapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.healthapp.data.db.ActivityGoal
import dev.healthapp.data.db.AppDatabase
import dev.healthapp.data.db.ExerciseSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every schema upgrade keeps the user's data. The old database is built from its exported schema
 * (schemas/…/N.json, the same tables and identity hash a real install has), filled with rows, then opened by the
 * current Room database with its migrations. Room itself checks that the migrated tables match the new schema.
 * (Room's MigrationTestHelper can't open files by path on Windows, hence building it by hand.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createVersion(name: String, version: Int, fill: (SQLiteDatabase) -> Unit) {
        val json = context.assets.open("dev.healthapp.data.db.AppDatabase/$version.json").bufferedReader().use { it.readText() }
        val schema = JSONObject(json).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            fill(db)
            db.version = version
        }
    }

    private fun open(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()

    @Test fun v1ToV2KeepsTheFoodLogAndAddsActivities() {
        val name = "migration-1-2.db"
        createVersion(name, 1) { db ->
            db.execSQL("INSERT INTO food_entry (id, day, time, name, meal, kcal, protein, carbs, fat, hcRecordId) " +
                "VALUES (1, '2026-03-01', 1000, 'TEST porridge', 'BREAKFAST', 350.0, 12.0, 55.0, 8.0, NULL)")
            db.execSQL("INSERT INTO weight_entry (id, day, time, kg, source, hcRecordId) VALUES (1, '2026-03-01', 1000, 70.5, 'manual', NULL)")
            db.execSQL("INSERT INTO daily_metric (day, steps, sources) VALUES ('2026-03-01', 9000, 'test')")
        }
        val db = open(name)
        try {
            runBlocking {
                val food = db.food().all()
                assertEquals(listOf("TEST porridge"), food.map { it.name })
                assertEquals(350.0, food.single().kcal, 1e-9)
                assertEquals(70.5, db.weight().all().single().kg, 1e-9)
                assertEquals(9000L, db.metrics().all().single().steps)

                db.exercise().upsert(listOf(ExerciseSession("s1", "2026-03-01", 0, 1_800_000, 56, null, "test")))
                assertEquals(1, db.exercise().all().size)
                db.goals().insert(ActivityGoal(category = null, metric = "SESSIONS", weeklyTarget = 3, created = 0))
                assertEquals(3, db.goals().all().first().single().weeklyTarget)
            }
        } finally {
            db.close()
        }
    }

    @Test fun v2ToV3AddsThePhotoEstimateColumn() {
        val name = "migration-2-3.db"
        createVersion(name, 2) { db ->
            db.execSQL("INSERT INTO food_entry (id, day, time, name, meal, kcal, protein, carbs, fat, hcRecordId) " +
                "VALUES (1, '2026-03-01', 1000, 'TEST soup', 'LUNCH', 420.0, 20.0, 40.0, 15.0, NULL)")
            db.execSQL("INSERT INTO activity_goal (id, category, metric, weeklyTarget, created) VALUES (1, NULL, 'MINUTES', 150, 0)")
        }
        val db = open(name)
        try {
            runBlocking {
                val old = db.food().all().single()
                assertEquals(null, old.photoEstimateKcal)
                assertEquals(150, db.goals().all().first().single().weeklyTarget)
                db.food().insert(old.copy(id = 0, name = "TEST photo", kcal = 600.0, photoEstimateKcal = 500.0))
                assertEquals(listOf(dev.healthapp.data.db.PhotoPair(500.0, 600.0)), db.food().photoPairs().first())
            }
        } finally {
            db.close()
        }
    }

    @Test fun v1ToV3RunsBothMigrations() {
        val name = "migration-1-3.db"
        createVersion(name, 1) { db ->
            db.execSQL("INSERT INTO food_entry (id, day, time, name, meal, kcal, protein, carbs, fat, hcRecordId) " +
                "VALUES (1, '2026-03-01', 1000, 'TEST toast', 'BREAKFAST', 200.0, 6.0, 30.0, 5.0, NULL)")
        }
        val db = open(name)
        try {
            runBlocking { assertEquals(listOf("TEST toast"), db.food().all().map { it.name }) }
        } finally {
            db.close()
        }
    }
}
