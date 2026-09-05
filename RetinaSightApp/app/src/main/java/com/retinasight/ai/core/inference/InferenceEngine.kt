package com.retinasight.ai.core.inference

import android.graphics.Bitmap
import com.retinasight.ai.core.model.RetinaResult

/**
 * The single seam between the UI and the model.
 *
 * The whole app depends only on this interface, so the real on-device engine
 * (TFLite INT8 + QNN delegate on the Hexagon NPU) can replace the development
 * mock without touching a single screen.
 *
 * Implementations must be safe to call from a background dispatcher.
 */
interface InferenceEngine {

    /** True once the model is loaded and ready. UI can show a spinner until then. */
    val isReady: Boolean

    /**
     * Loads the model into memory. Call once at app start so the first scan is
     * not slowed by a cold load - the phone keeps the model warm afterwards.
     * Safe to call more than once.
     */
    suspend fun warmUp()

    /**
     * Analyses one fundus image entirely on-device.
     *
     * @param image the captured or imported fundus photo
     * @param languageTag BCP-47 tag of the user's language, e.g. "hi", "mr"
     * @return a result derived from this specific image - never a canned value
     */
    suspend fun analyze(image: Bitmap, languageTag: String): RetinaResult

    /** Releases native resources. */
    fun close()
}
