package io.github.meko123456.zari.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeStatusTest {

    private val sources = listOf("VOICE_COMMUNICATION" to 12, "VOICE_RECOGNITION" to 980, "MIC" to null)

    private fun text(isProbing: Boolean, results: List<Pair<String, Int?>>, outsideCall: Boolean = false) =
        ProbeStatus.text(isProbing, results, total = 3, outsideCall = outsideCall, audibleThreshold = 150)

    @Test
    fun `nothing to say before a probe`() {
        assertEquals("", text(isProbing = false, results = emptyList()))
    }

    @Test
    fun `measuring, then how far it got`() {
        assertEquals("Measuring…", text(isProbing = true, results = emptyList()))
        assertEquals("Measuring: 2 of 3 sources done", text(isProbing = true, results = sources.take(2)))
    }

    @Test
    fun `during a call, the first source that hears audio is the answer`() {
        assertEquals("Measured during a call: VOICE_RECOGNITION hears audio, peak 980.", text(isProbing = false, results = sources))
    }

    @Test
    fun `during a call with every source silent or refused`() {
        val silent = listOf("VOICE_COMMUNICATION" to 12, "MIC" to null)
        assertEquals("Measured during a call: every source is silent.", text(isProbing = false, results = silent))
    }

    @Test
    fun `outside a call the numbers prove nothing, however healthy`() {
        assertEquals(
            "Measured with no call in progress, so this says nothing about call recording — every source works outside a call. Run it again during one.",
            text(isProbing = false, results = sources, outsideCall = true),
        )
    }
}
