package dev.healthapp

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.healthapp.ml.CalorieEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug builds only. Runs CalorieK over every image in files/bench/ (private storage) and writes bench/results.csv
 * (file, kcal, mass, protein, carb, fat, decode+preprocess ms, inference ms), so phone output can be compared
 * with the PC benchmark and timed on real hardware.
 *
 *   adb shell am start -n dev.healthapp/.BenchActivity [--ez xnnpack true]
 *   adb logcat -s CalorieBench
 */
class BenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply { text = "Running CalorieK benchmark…"; setPadding(48, 160, 48, 48) }
        setContentView(view)
        val xnnpack = intent.getBooleanExtra("xnnpack", false)
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.Default) { run(xnnpack) }
            Log.i(TAG, summary)
            view.text = summary
        }
    }

    private fun run(xnnpack: Boolean): String {
        val est = CalorieEstimator(this)
        val dir = File(filesDir, "bench")
        val files = dir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return "No images in ${dir.absolutePath}"
        est.session(useXnnpack = xnnpack)
        Log.i(TAG, "session load ${est.loadMs} ms (xnnpack=$xnnpack)")
        val lines = mutableListOf("file,kcal,mass_g,protein_g,carb_g,fat_g,prep_ms,infer_ms")
        val infer = mutableListOf<Long>()
        val prep = mutableListOf<Long>()
        files.forEachIndexed { i, f ->
            val t = SystemClock.elapsedRealtime()
            val bmp = est.decode(android.net.Uri.fromFile(f))
            val p = SystemClock.elapsedRealtime() - t
            val e = est.estimate(bmp)
            prep += p; infer += e.inferenceMs
            lines += listOf(f.name, e.kcal, e.massG, e.proteinG, e.carbG, e.fatG, p, e.inferenceMs).joinToString(",")
            if (i % 10 == 0) Log.i(TAG, "${i + 1}/${files.size}")
        }
        File(dir, "results${if (xnnpack) "_xnnpack" else ""}.csv").writeText(lines.joinToString("\n"))
        val warm = infer.drop(1).sorted()
        return "CalorieK on ${files.size} images, xnnpack=$xnnpack: session load ${est.loadMs} ms, " +
            "first inference ${infer.first()} ms, median ${warm[warm.size / 2]} ms, p90 ${warm[(warm.size * 9) / 10]} ms, " +
            "median decode+resize ${prep.sorted()[prep.size / 2]} ms"
    }

    companion object { const val TAG = "CalorieBench" }
}
