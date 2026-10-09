// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Jose Luis Alcazar and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DebugDumpTest {
    @Test
    fun `the file name carries the moment it was saved`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            // 2026-10-09 10:11:12 UTC
            assertEquals("MotoVisor-debug-20261009-101112.txt", DebugDump.fileName(1_791_540_672_000L))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `settings come first, one per line, in the order given`() {
        val block = DebugDump.settingsBlock(linkedMapOf("powerMode" to "ECO", "autoRecovery" to "true"))
        val lines = block.lines()
        assertEquals("Settings", lines[0])
        assertEquals("  powerMode: ECO", lines[1])
        assertEquals("  autoRecovery: true", lines[2])
        assertTrue(lines[3].all { it == '-' } && lines[3].isNotEmpty())
    }

    @Test
    fun `only the newest session files are kept`() {
        val files = (1..12).map { day -> "MotoVisor-debug-202610%02d-080000.txt".format(day) to day }
        // Handed over out of order, as a MediaStore query may return them.
        val doomed = DebugDump.surplus(files.shuffled(java.util.Random(7)), keep = 10)
        assertEquals(listOf(2, 1), doomed)
    }

    @Test
    fun `nothing is deleted until there are more files than are kept`() {
        val files = (1..10).map { day -> "MotoVisor-debug-202610%02d-080000.txt".format(day) to day }
        assertTrue(DebugDump.surplus(files, keep = 10).isEmpty())
    }
}
