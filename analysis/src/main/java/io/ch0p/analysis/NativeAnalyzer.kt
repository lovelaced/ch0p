package io.ch0p.analysis

/**
 * Kotlin handle to the native scene/motion detector (libch0panalysis).
 *
 * Feed sampled, downscaled RGB frames in presentation order, then read the shot
 * boundaries and the per-sample motion curve. Backed by C++ in src/main/cpp; the same
 * algorithm is exercised on the host via tools/hosttest.sh.
 */
class NativeAnalyzer : AutoCloseable {

    private var handle: Long = nativeCreate()

    /** rgb = interleaved R,G,B bytes, width*height*3. */
    fun pushFrame(rgb: ByteArray, width: Int, height: Int, tSec: Double) {
        check(handle != 0L) { "analyzer closed" }
        nativePushFrame(handle, rgb, width, height, tSec)
    }

    fun cutTimesSec(): DoubleArray = nativeCutTimes(handle)
    fun motionCurve(): FloatArray = nativeMotion(handle)
    fun sharpnessCurve(): FloatArray = nativeSharpness(handle)
    fun colorfulnessCurve(): FloatArray = nativeColorfulness(handle)
    fun sampleTimesSec(): DoubleArray = nativeSampleTimes(handle)

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativePushFrame(handle: Long, rgb: ByteArray, width: Int, height: Int, tSec: Double)
    private external fun nativeCutTimes(handle: Long): DoubleArray
    private external fun nativeMotion(handle: Long): FloatArray
    private external fun nativeSharpness(handle: Long): FloatArray
    private external fun nativeColorfulness(handle: Long): FloatArray
    private external fun nativeSampleTimes(handle: Long): DoubleArray
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("ch0panalysis") }
    }
}
