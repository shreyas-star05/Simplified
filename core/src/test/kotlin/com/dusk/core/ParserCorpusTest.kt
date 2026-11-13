package com.dusk.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ParserCorpusTest {
    private fun cases() = CorpusCheck.load(
        javaClass.getResourceAsStream("/corpus.tsv")!!.bufferedReader().readText(),
    )

    @Test
    fun corpusHasTheSpecSentences() {
        assertTrue("corpus is too small", cases().size >= 70)
    }

    @Test
    fun everySentenceProducesTheExpectedCard() {
        val failures = CorpusCheck.failures(cases())
        assertTrue("\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
