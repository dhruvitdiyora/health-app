package dev.healthapp.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * One photo estimate. CalorieK is off by roughly 25-35% on everyday meals (docs/food-model-benchmark.md),
 * so the range is what the UI leads with, not the point value.
 */
data class PhotoEstimate(
    val kcal: Double,
    val massG: Double,
    val proteinG: Double,
    val carbG: Double,
    val fatG: Double,
    val inferenceMs: Long,
) {
    /**
     * Half-width of the range: the larger of CalorieK's own 90% calibration radius (119 kcal, on its
     * cafeteria test set) and 30%, about the typical error we measured on everyday photos.
     */
    val radius: Double get() = max(119.0, 0.3 * kcal)
    val low: Double get() = (kcal - radius).coerceAtLeast(0.0)
    val high: Double get() = kcal + radius
}

/**
 * CalorieK (SigLIP2-base + regression head), exported to ONNX fp32. The model file is not in the APK
 * (375 MB): it lives in this app's private files dir (files/models/) and is copied there with adb for now:
 *   adb exec-in run-as dev.healthapp sh -c 'mkdir -p files/models && cat > files/models/caloriek_fp32.onnx' < caloriek_fp32.onnx
 * Output order: kcal, mass_g, protein_g, carb_g, fat_g.
 */
class CalorieEstimator(private val context: Context) {

    val modelFile: File get() = File(File(context.filesDir, "models"), MODEL_NAME)

    val isInstalled: Boolean get() = modelFile.length() > 100_000_000

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null
    var loadMs: Long = 0
        private set

    /** Creating the session takes a few seconds for 375 MB; done once, then kept. */
    @Synchronized
    fun session(useXnnpack: Boolean = false): OrtSession {
        session?.let { return it }
        check(isInstalled) { "Photo model not installed" }
        val t = SystemClock.elapsedRealtime()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (useXnnpack) addXnnpack(mapOf("intra_op_num_threads" to "4"))
        }
        return env.createSession(modelFile.absolutePath, options).also {
            session = it
            loadMs = SystemClock.elapsedRealtime() - t
        }
    }

    @Synchronized
    fun close() {
        session?.close()
        session = null
    }

    suspend fun warmUp() = withContext(Dispatchers.Default) { if (isInstalled) session() }

    suspend fun estimate(uri: Uri): PhotoEstimate = withContext(Dispatchers.Default) {
        estimate(decode(uri))
    }

    fun estimate(bitmap: Bitmap): PhotoEstimate {
        val s = session()
        val input = preprocess(bitmap)
        val t = SystemClock.elapsedRealtime()
        OnnxTensor.createTensor(env, input, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            s.run(mapOf(s.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val y = (result[0].value as Array<FloatArray>)[0]
                return PhotoEstimate(y[0].toDouble(), y[1].toDouble(), y[2].toDouble(), y[3].toDouble(), y[4].toDouble(),
                    SystemClock.elapsedRealtime() - t)
            }
        }
    }

    /** Decodes with EXIF orientation applied (ImageDecoder), subsampled so the short side stays >= 448 px. */
    fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                var sample = 1
                while (minOf(info.size.width, info.size.height) / (sample * 2) >= 2 * SIZE) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("Could not read the photo")
        }

    companion object {
        const val MODEL_NAME = "caloriek_fp32.onnx"
        const val SIZE = 224

        /**
         * SigLIP preprocessing: squash to 224 x 224 (aspect not kept), then (x / 255 - 0.5) / 0.5, CHW.
         * PIL's bilinear resize antialiases; Android's filtered scaling doesn't, so large photos are halved
         * step by step first (an exact 2x bilinear step averages 2 x 2 pixels).
         */
        fun preprocess(source: Bitmap): FloatBuffer {
            var bmp = source
            while (bmp.width >= 2 * SIZE || bmp.height >= 2 * SIZE) {
                val w = if (bmp.width >= 2 * SIZE) bmp.width / 2 else bmp.width
                val h = if (bmp.height >= 2 * SIZE) bmp.height / 2 else bmp.height
                bmp = Bitmap.createScaledBitmap(bmp, w, h, true)
            }
            bmp = Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
            val px = IntArray(SIZE * SIZE)
            bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            val plane = SIZE * SIZE
            val out = FloatBuffer.allocate(3 * plane)
            for (i in 0 until plane) {
                val c = px[i]
                out.put(i, ((c shr 16 and 0xFF) / 255f - 0.5f) / 0.5f)
                out.put(plane + i, ((c shr 8 and 0xFF) / 255f - 0.5f) / 0.5f)
                out.put(2 * plane + i, ((c and 0xFF) / 255f - 0.5f) / 0.5f)
            }
            return out
        }
    }
}
