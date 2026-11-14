package com.dusk.core

import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    @Test
    fun release1CoreChecksPass() {
        val failures = CoreChecks.run()
        assertTrue("\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
