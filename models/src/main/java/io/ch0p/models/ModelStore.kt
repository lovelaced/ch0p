package io.ch0p.models

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages optional model files in app-private storage: presence, on-demand download with
 * SHA-256 verification, and deletion. Capability-based — callers consult
 * [DeviceCapability] before offering a download.
 *
 * [download] is blocking with progress + cancel; the app runs it off the main thread.
 */
class ModelStore(context: Context) {

    private val dir: File = File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(spec: ModelSpec): File = File(dir, fileName(spec))

    fun isInstalled(spec: ModelSpec): Boolean =
        spec.isSystemProvided || fileFor(spec).exists()

    fun installed(): List<ModelSpec> = ModelCatalog.all.filter { isInstalled(it) }

    /** Premium features currently unlocked on this device by installed models. */
    fun activeFeatures(): List<Feature> = ModelCatalog.featuresFor(installed().map { it.id }.toSet())

    fun totalInstalledBytes(): Long =
        ModelCatalog.all.filter { !it.isSystemProvided && fileFor(it).exists() }
            .sumOf { fileFor(it).length() }

    fun delete(spec: ModelSpec): Boolean = fileFor(spec).let { if (it.exists()) it.delete() else false }

    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    /**
     * Downloads [spec] to app storage, verifying SHA-256 if pinned. Returns the file, or
     * null if cancelled. Throws on network/integrity failure.
     */
    fun download(
        spec: ModelSpec,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        progress: Progress = Progress {},
    ): File? {
        require(!spec.isSystemProvided) { "${spec.id} is provided by the OS, not downloadable" }
        val url = spec.url ?: error("${spec.id} has no download URL pinned yet")

        val dest = fileFor(spec)
        val tmp = File(dest.absolutePath + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            conn.connect()
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: spec.approxSizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var copied = 0L
                    while (true) {
                        if (cancelled.get()) { tmp.delete(); return null }
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        copied += n
                        if (total > 0) progress.onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val hex = digest.digest().toHex()
            if (spec.sha256 != null && !hex.equals(spec.sha256, ignoreCase = true)) {
                tmp.delete()
                error("Checksum mismatch for ${spec.id}: got $hex")
            }
            if (dest.exists()) dest.delete()
            check(tmp.renameTo(dest)) { "Could not finalize ${spec.id}" }
            progress.onProgress(1f)
            return dest
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun fileName(spec: ModelSpec): String {
        val fromUrl = spec.url?.substringAfterLast('/')?.takeIf { it.contains('.') }
        return fromUrl ?: "${spec.id}.model"
    }
}

/** Pure SHA-256 helpers (unit-testable). */
object Sha256 {
    fun ofBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun ofFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }
}

internal fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }
