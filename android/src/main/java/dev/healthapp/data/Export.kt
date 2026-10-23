package dev.healthapp.data

import dev.healthapp.data.db.AppDatabase
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes everything in the local database as CSV files inside one zip. */
object Export {
    suspend fun writeZip(db: AppDatabase, out: OutputStream) {
        val food = db.food().all()
        val weight = db.weight().all()
        val daily = db.metrics().all()
        val sleep = db.sleep().all()
        ZipOutputStream(out).use { zip ->
            zip.csv("food.csv", listOf("day", "time", "meal", "name", "kcal", "protein_g", "carbs_g", "fat_g"),
                food.map { listOf(it.day, iso(it.time), it.meal.name, it.name, it.kcal, it.protein, it.carbs, it.fat) })
            zip.csv("weight.csv", listOf("day", "time", "kg", "source"),
                weight.map { listOf(it.day, iso(it.time), it.kg, it.source) })
            zip.csv("daily.csv", listOf("day", "steps", "active_kcal", "total_kcal", "exercise_min", "resting_hr", "hrv_ms", "sources"),
                daily.map { listOf(it.day, it.steps, it.activeKcal, it.totalKcal, it.exerciseMinutes, it.restingHr, it.hrvMs, it.sources) })
            zip.csv("sleep.csv", listOf("wake_day", "start", "end", "asleep_min", "deep_min", "rem_min", "light_min", "awake_min", "source"),
                sleep.map { listOf(it.day, iso(it.start), iso(it.end), it.asleepMinutes, it.deepMinutes, it.remMinutes, it.lightMinutes, it.awakeMinutes, it.source) })
        }
    }

    private fun iso(millis: Long) = Instant.ofEpochMilli(millis).toString()

    private fun ZipOutputStream.csv(name: String, header: List<String>, rows: List<List<Any?>>) {
        putNextEntry(ZipEntry(name))
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append('\n')
        for (row in rows) sb.append(row.joinToString(",") { escape(it) }).append('\n')
        write(sb.toString().toByteArray())
        closeEntry()
    }

    internal fun escape(value: Any?): String {
        val s = when (value) {
            null -> ""
            is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, value)
            else -> value.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
