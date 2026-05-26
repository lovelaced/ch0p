package io.ch0p.ingest

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies a source (often a slow SAF stream off an SD card / USB volume that can vanish
 * mid-job) into stable app-private storage before any heavy processing.
 *
 * Blocking by design with a progress callback and a cancel flag; callers run it off the
 * main thread. No coroutine dependency here — the app layer wraps it.
 */
object WorkingCopy {

    private const val BUFFER = 1 shl 20  // 1 MiB

    fun sourcesDir(context: Context): File =
        File(context.filesDir, "sources").apply { mkdirs() }

    /**
     * @param totalBytes expected size for progress (0 = unknown).
     * @param onProgress fraction 0..1 (only meaningful when totalBytes > 0).
     * @return the copied file, or null if cancelled.
     */
    fun copy(
        context: Context,
        uri: Uri,
        totalBytes: Long,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Float) -> Unit = {},
    ): File? {
        val dest = File(sourcesDir(context), "${UUID.randomUUID()}.mp4")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open input stream for $uri")
        try {
            input.use { inp ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(BUFFER)
                    var copied = 0L
                    while (true) {
                        if (cancelled.get()) {
                            dest.delete()
                            return null
                        }
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (totalBytes > 0) onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                    out.flush()
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        onProgress(1f)
        return dest
    }
}
