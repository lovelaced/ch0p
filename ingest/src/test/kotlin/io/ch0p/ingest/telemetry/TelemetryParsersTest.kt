package io.ch0p.ingest.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryParsersTest {

    // --- byte builders ---

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
    private fun u32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun s16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    /** A GPMF KLV node with 4-byte payload alignment. */
    private fun klv(key: String, type: Int, structSize: Int, repeat: Int, payload: ByteArray): ByteArray {
        val pad = (((payload.size + 3) and 3.inv()) - payload.size)
        return key.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(type.toByte(), structSize.toByte()) + u16(repeat) +
            payload + ByteArray(pad)
    }

    /** An ISO-BMFF box: size(4) + type(4) + payload. */
    private fun box(type: String, payload: ByteArray): ByteArray =
        u32((8 + payload.size).toLong()) + type.toByteArray(Charsets.US_ASCII) + payload

    // --- GPMF ---

    @Test fun `gpmf accelerometer magnitudes parse and scale`() {
        // ACCL: two samples (3,0,4)->5 and (0,0,0)->0; SCAL = 1.
        val scal = klv("SCAL", 's'.code, 2, 1, s16(1))
        val acclPayload = s16(3) + s16(0) + s16(4) + s16(0) + s16(0) + s16(0)
        val accl = klv("ACCL", 's'.code, 6, 2, acclPayload)
        val strm = klv("STRM", 0, 1, (scal + accl).size, scal + accl)
        val devc = klv("DEVC", 0, 1, strm.size, strm)

        val mags = Gpmf.acclMagnitudes(devc)
        assertEquals(2, mags.size)
        assertEquals(5f, mags[0], 1e-3f)
        assertEquals(0f, mags[1], 1e-3f)
    }

    @Test fun `gpmf tree navigation finds nested keys`() {
        val accl = klv("ACCL", 's'.code, 6, 1, s16(1) + s16(2) + s16(3))
        val strm = klv("STRM", 0, 1, accl.size, accl)
        val devc = klv("DEVC", 0, 1, strm.size, strm)
        val top = Gpmf.parse(devc).first()
        assertNotNull(top.find("ACCL"))
        assertEquals(1, top.findAll("STRM").size)
    }

    // --- HiLight tags ---

    @Test fun `hmmt payload yields sorted millisecond marks`() {
        val payload = u32(3) + u32(2500) + u32(1000) + u32(8000)
        val marks = HiLightTags.parsePayload(payload, 0, payload.size)
        assertEquals(listOf(1000L, 2500L, 8000L), marks)
    }

    @Test fun `hilights extracted from a synthesized moov`() {
        val hmmt = box("HMMT", u32(2) + u32(1500) + u32(4200))
        val udta = box("udta", hmmt)
        // moov contains some other box plus udta, to exercise sibling skipping.
        val moovPayload = box("mvhd", ByteArray(20)) + udta
        val moov = box("moov", moovPayload)
        // strip the outer moov header — fromMoov expects the moov *payload* buffer.
        val moovBody = moov.copyOfRange(8, moov.size)
        assertEquals(listOf(1500L, 4200L), HiLightTags.fromMoov(moovBody))
    }

    // --- box reader ---

    @Test fun `findByPath resolves nested boxes`() {
        val inner = box("HMMT", u32(1) + u32(999))
        val udta = box("udta", inner)
        val moov = box("moov", box("mvhd", ByteArray(4)) + udta)
        val found = Mp4Boxes.findByPath(moov, "moov/udta/HMMT")
        assertNotNull(found)
        assertEquals("HMMT", found!!.type)
    }

    @Test fun `missing path returns null`() {
        val moov = box("moov", box("mvhd", ByteArray(4)))
        assertTrue(Mp4Boxes.findByPath(moov, "moov/udta/HMMT") == null)
    }
}
