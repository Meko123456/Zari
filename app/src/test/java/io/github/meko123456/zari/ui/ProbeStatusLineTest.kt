package io.github.meko123456.zari.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The measuring status line. TalkBack's speech can't be observed from a test, so this checks what
 * TalkBack acts on: the line is a polite live region, and each step is a change of the same node's
 * words, not a node appearing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ProbeStatusLineTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun eachStepOfAProbeIsANewWordingOfOnePoliteLiveRegion() {
        var text by mutableStateOf("")
        rule.setContent { ProbeStatusLine(text, error = false) }
        val line = rule.onNodeWithTag(PROBE_STATUS_TAG, useUnmergedTree = true)
        line.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        line.assertHeightIsEqualTo(0.dp)
        val node = line.fetchSemanticsNode().id

        text = "Measuring…"
        line.assertTextEquals("Measuring…")
        assertEquals(node, line.fetchSemanticsNode().id)

        text = "Measuring: 2 of 3 sources done"
        line.assertTextEquals("Measuring: 2 of 3 sources done")
        assertEquals(node, line.fetchSemanticsNode().id)

        text = "Measured during a call: VOICE_RECOGNITION hears audio, peak 980."
        line.assertTextEquals("Measured during a call: VOICE_RECOGNITION hears audio, peak 980.")
        assertEquals(node, line.fetchSemanticsNode().id)
        line.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
