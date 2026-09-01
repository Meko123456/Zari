package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNamesTest {

    private val stamp: (Long) -> String = { "2026-09-01_14-30-00" }

    @Test
    fun `the name carries date direction and who so the file survives without the index`() {
        val name = FileNames.forCall(0, "+995322123456", "Giorgi", CallDirection.INCOMING, stamp)
        assertEquals("2026-09-01_14-30-00_in_Giorgi.m4a", name)
    }

    @Test
    fun `an unknown caller still produces a usable name`() {
        val name = FileNames.forCall(0, null, null, CallDirection.UNKNOWN, stamp)
        assertEquals("2026-09-01_14-30-00_call_unknown.m4a", name)
    }

    @Test
    fun `a plus keeps its meaning in an international number`() {
        val name = FileNames.forCall(0, "+971501234567", null, CallDirection.OUTGOING, stamp)
        assertTrue(name.contains("+971501234567"), name)
    }

    @Test
    fun `characters that break filesystems are replaced`() {
        assertEquals("a-b-c", FileNames.sanitise("a/b:c"))
        assertEquals("Anna-Maria", FileNames.sanitise("Anna  Maria"))
        assertFalse(FileNames.sanitise("../../etc/passwd").contains(".."))
    }

    @Test
    fun `a name of nothing but punctuation does not become an empty file name`() {
        assertEquals("unknown", FileNames.sanitise("///"))
        assertEquals("unknown", FileNames.sanitise("   "))
    }

    @Test
    fun `a very long contact name is capped well inside the filesystem limit`() {
        val long = FileNames.sanitise("Giorgi ".repeat(40))
        assertTrue(long.length <= 48, "was ${long.length}")
    }

    @Test
    fun `georgian and arabic names survive because they are letters`() {
        assertEquals("გიორგი", FileNames.sanitise("გიორგი"))
        assertEquals("محمد", FileNames.sanitise("محمد"))
    }
}
