package io.ch0p.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManagerTest {

    // --- catalog integrity ---

    @Test fun `model ids are unique`() {
        val ids = ModelCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `every feature has at least one model`() {
        for (f in Feature.entries) {
            assertTrue("no model for $f", ModelCatalog.forFeature(f).isNotEmpty())
        }
    }

    @Test fun `system-provided models have no url, downloadable ones declare runtime`() {
        for (spec in ModelCatalog.all) {
            if (spec.isSystemProvided) assertEquals(null, spec.url)
            assertTrue("bad ram floor for ${spec.id}", spec.minRamMb > 0)
        }
    }

    @Test fun `byId resolves`() {
        assertNotNull(ModelCatalog.byId("whisper-base-q5"))
        assertEquals(null, ModelCatalog.byId("nope"))
    }

    // --- capability gating ---

    private fun device(ramMb: Int, arm64: Boolean = true) =
        DeviceCapability(ramMb, "test-soc", if (arm64) listOf("arm64-v8a") else listOf("x86"))

    @Test fun `non-arm64 device runs nothing`() {
        assertTrue(device(12000, arm64 = false).runnable().isEmpty())
    }

    @Test fun `low-ram device is gated off heavy models`() {
        val d = device(4000)
        assertTrue("silero fits", d.canRun(ModelCatalog.byId("silero-vad")!!))
        assertTrue("whisper-base fits", d.canRun(ModelCatalog.byId("whisper-base-q5")!!))
        assertFalse("whisper-small too big", d.canRun(ModelCatalog.byId("whisper-small-q5")!!))
    }

    @Test fun `flagship runs the heavy models`() {
        val d = device(12000)
        assertTrue(d.isFlagshipClass)
        assertTrue(d.canRun(ModelCatalog.byId("whisper-small-q5")!!))
        assertTrue(d.availableFeatures().contains(Feature.SEMANTIC_SELECTION))
    }

    @Test fun `captions feature available even on mid-range`() {
        assertTrue(device(4000).availableFeatures().contains(Feature.AUTO_CAPTIONS))
    }

    // --- hashing ---

    @Test fun `featuresFor maps installed ids to features`() {
        val active = ModelCatalog.featuresFor(setOf("yamnet", "whisper-base-q5"))
        assertTrue(active.contains(Feature.LAUGHTER_DETECTION))
        assertTrue(active.contains(Feature.AUTO_CAPTIONS))
        assertFalse(active.contains(Feature.SMART_REFRAME))
        assertTrue(ModelCatalog.featuresFor(emptySet()).isEmpty())
    }

    @Test fun `sha256 of abc matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.ofBytes("abc".toByteArray()),
        )
    }
}
