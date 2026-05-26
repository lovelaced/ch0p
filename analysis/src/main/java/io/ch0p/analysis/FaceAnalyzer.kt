package io.ch0p.analysis

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import java.io.File
import java.nio.ByteBuffer

/**
 * MediaPipe Face Detector (BlazeFace) over sampled frames → a per-frame face signal
 * (presence/area/centeredness) that feeds the interest channel and the reframe target.
 * Construct only when the model is installed; device-only. Scoring is delegated to the
 * pure [FaceScoring] (JVM-tested).
 */
class FaceAnalyzer(context: Context, modelPath: String) : AutoCloseable {

    private val detector: FaceDetector = FaceDetector.createFromOptions(
        context,
        FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(directBufferOf(File(modelPath))).build())
            .setMinDetectionConfidence(0.5f)
            .setRunningMode(RunningMode.IMAGE)
            .build(),
    )

    fun detect(bitmap: Bitmap): FaceScoring.FaceFrame {
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        val boxes = result.detections().map {
            val b = it.boundingBox()
            floatArrayOf(b.left, b.top, b.right, b.bottom)
        }
        return FaceScoring.fromBoxes(boxes, bitmap.width, bitmap.height)
    }

    override fun close() = detector.close()

    private companion object {
        fun directBufferOf(file: File): ByteBuffer {
            val bytes = file.readBytes()
            return ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
        }
    }
}
