package io.ch0p.render

/**
 * Parses a preset aspect-ratio string ("9:16", "16:9", "2.39:1", "1:1") into a
 * width/height float. Pure — unit-tested on the JVM.
 */
object AspectRatio {

    const val DEFAULT = 9f / 16f  // vertical, the common shorts case

    fun parseOrDefault(spec: String): Float = parseOrNull(spec) ?: DEFAULT

    fun parseOrNull(spec: String): Float? {
        val parts = spec.split(":")
        if (parts.size != 2) return null
        val w = parts[0].trim().toFloatOrNull() ?: return null
        val h = parts[1].trim().toFloatOrNull() ?: return null
        if (w <= 0f || h <= 0f) return null
        return w / h
    }
}
