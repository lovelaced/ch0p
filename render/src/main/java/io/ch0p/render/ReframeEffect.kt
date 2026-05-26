package io.ch0p.render

import android.graphics.Matrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import io.ch0p.edit.reframe.CropKeyframe
import io.ch0p.edit.reframe.CropTrajectory
import io.ch0p.edit.reframe.Reframe

/**
 * Per-clip animated reframe: samples the precomputed crop trajectory at each frame's source
 * time and returns a pan/zoom matrix that nudges the subject toward center. Runs before the
 * aspect-crop [androidx.media3.effect.Presentation], so the visible 9:16 window follows the
 * subject. Math lives in the pure [Reframe]/[CropTrajectory] (JVM-tested); this is the thin
 * Media3 adapter.
 */
@UnstableApi
class ReframeEffect(
    private val trajectory: List<CropKeyframe>,
    private val clipStartMs: Long,
) : MatrixTransformation {

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val matrix = Matrix()
        val srcTimeSec = clipStartMs / 1000.0 + presentationTimeUs / 1_000_000.0
        val kf = CropTrajectory.sampleAt(trajectory, srcTimeSec) ?: return matrix
        val p = Reframe.params(kf.cx.toFloat(), kf.cy.toFloat())
        matrix.postScale(p.scale, p.scale)
        matrix.postTranslate(p.tx, p.ty)
        return matrix
    }
}
