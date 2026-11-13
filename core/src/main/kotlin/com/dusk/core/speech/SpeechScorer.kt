package com.dusk.core.speech

/** Result of comparing what an engine heard with the script that was read aloud. */
data class SpeechScore(
    val refWords: Int,
    val hypWords: Int,
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val sentencesTotal: Int,
    val sentencesDropped: Int,
    val droppedExamples: List<String>,
) {
    /** Word error rate in percent. Numbers are left out of the comparison on both sides. */
    val werPercent: Double
        get() = if (refWords == 0) 0.0 else 100.0 * (substitutions + deletions + insertions) / refWords

    fun summary(): String =
        "WER ${"%.1f".format(werPercent)}% (wrong $substitutions, missed $deletions, extra $insertions of $refWords words); " +
            "sentences dropped $sentencesDropped of $sentencesTotal"
}

object SpeechScorer {

    private val NUMBER_WORDS = setOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
        "hundred", "thousand", "lakh", "first", "second", "third", "fourth", "fifth", "sixth",
        "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth", "am", "pm", "rs", "rupees", "rupee",
    )

    /** Lowercase words with punctuation removed. Numbers and number words are dropped. */
    fun words(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('\'') }
            .filter { it.isNotEmpty() && it.none(Char::isDigit) && it !in NUMBER_WORDS }

    fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    fun score(reference: String, hypothesis: String): SpeechScore {
        val sents = sentences(reference)
        val ref = ArrayList<String>()
        val sentOf = ArrayList<Int>()
        sents.forEachIndexed { si, s -> words(s).forEach { ref += it; sentOf += si } }
        val hyp = words(hypothesis)
        val n = ref.size
        val m = hyp.size
        // Edit distance over words, with a backtrace to see which script words were matched.
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val sub = cost[i - 1][j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
            cost[i][j] = minOf(sub, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
        }
        var subs = 0; var dels = 0; var ins = 0
        val matchedPerSentence = IntArray(sents.size)
        val wordsPerSentence = IntArray(sents.size)
        sentOf.forEach { wordsPerSentence[it]++ }
        var i = n; var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && ref[i - 1] == hyp[j - 1] && cost[i][j] == cost[i - 1][j - 1]) {
                matchedPerSentence[sentOf[i - 1]]++; i--; j--
            } else if (i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + 1) {
                subs++; i--; j--
            } else if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
                dels++; i--
            } else {
                ins++; j--
            }
        }
        val dropped = ArrayList<String>()
        for (si in sents.indices) {
            val total = wordsPerSentence[si]
            if (total >= 4 && matchedPerSentence[si] < 0.4 * total) dropped += sents[si]
        }
        return SpeechScore(n, m, subs, dels, ins, sents.size, dropped.size, dropped.take(5))
    }
}
