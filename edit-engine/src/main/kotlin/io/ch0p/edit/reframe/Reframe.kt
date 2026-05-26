package io.ch0p.edit.reframe

/** Pan/zoom for the reframe transform in NDC (-1..1). Pure & JVM-testable. */
data class ReframeParams(val scale: Float, val tx: Float, val ty: Float)

object Reframe {
    const val ZOOM = 1.15f       // slight zoom-in gives pan headroom before black edges
    const val MAX_PAN_X = 0.15f
    const val MAX_PAN_Y = 0.10f

    /**
     * Pan/zoom that nudges the subject toward frame center.
     * @param cx,cy normalized subject center in [0,1] (image space, y down).
     */
    fun params(cx: Float, cy: Float): ReframeParams {
        val sx = cx * 2f - 1f      // NDC x (+1 = right)
        val sy = 1f - 2f * cy      // NDC y (+1 = top)
        val tx = (-sx).coerceIn(-MAX_PAN_X, MAX_PAN_X)
        val ty = (-sy * 0.5f).coerceIn(-MAX_PAN_Y, MAX_PAN_Y)  // gentler vertical follow
        return ReframeParams(ZOOM, tx, ty)
    }
}
