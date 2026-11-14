package com.dusk.core.journal

import java.time.DayOfWeek

/** The prompt card from Dusk, carried over unchanged. A reference while speaking, not a form. */
data class Lens(val id: String, val name: String, val questions: List<String>, val why: String)

object Prompts {
    const val INTRO = "Speak in any order. Glance down the list and answer whatever feels alive tonight. Three or four lenses is plenty."

    val LENSES = listOf(
        Lens("feel", "Name the feeling", listOf(
            "Which two or three words fit today best? Reach for precise ones: calm, restless, proud, flat, hopeful, irritated.",
            "When was the feeling strongest, and what was happening right then?",
            "Did it shift during the day?",
        ), "Putting a feeling into precise words dampens the brain's alarm response (Lieberman et al., 2007)."),
        Lens("replay", "Replay the day", listOf(
            "Walk through morning, afternoon and evening. What were you doing, and with whom?",
            "What did you eat, watch, read or search for?",
            "Which moment would you keep, and which would you skip?",
        ), "Rebuilding the day moment by moment gives a truer picture than a one-line summary (Day Reconstruction Method, Kahneman et al., 2004)."),
        Lens("body", "Body and energy", listOf(
            "How did you sleep, and how did your energy move through the day?",
            "Did you train, walk or move? How did your body feel?",
            "Anything you ate or drank that you noticed afterwards?",
        ), "Energy is the backdrop to mood. Saying it daily lets the weekly report line sleep, food and training up against how you felt."),
        Lens("progress", "Progress and small wins", listOf(
            "What moved forward today, even a little?",
            "What got in the way?",
            "Was the work deep and focused, or scattered?",
        ), "Across about 12,000 daily diaries, making progress on meaningful work was the strongest driver of good days (Amabile & Kramer, 2011)."),
        Lens("good", "Three good things", listOf(
            "Name three things that went well today, big or tiny.",
            "For each one, what made it happen?",
        ), "Doing this nightly for one week raised happiness and lowered depressive symptoms for up to six months (Seligman et al., 2005)."),
        Lens("money", "Money with meaning", listOf(
            "What did you spend on today, and roughly how much?",
            "Which spend was worth it, and which wasn't?",
            "Was it for an experience, a thing, or someone else?",
        ), "Spending on experiences, on other people and on small pleasures tends to buy more happiness than things do (Dunn, Gilbert & Wilson, 2011)."),
        Lens("people", "People", listOf(
            "Who did you talk to or spend time with?",
            "Who gave you energy, and who drained it?",
            "Anything you wish you had said?",
        ), "Uplifting interactions were the second big driver of good days in the same diary research (Amabile & Kramer, 2011)."),
        Lens("direction", "Direction", listOf(
            "What did today say about what matters to you?",
            "Did today move you toward the person you want to become, or away?",
            "What thought kept coming back?",
        ), "\"What\" questions lead to insight; \"why\" questions tend to spiral into rumination (Eurich, 2017). Picturing your best possible self builds optimism (King, 2001)."),
        Lens("tomorrow", "Tomorrow, if–then", listOf(
            "What is one thing for tomorrow?",
            "Say it as a plan: \"If [situation], then I will [action].\"",
        ), "If–then plans make follow-through much more likely than good intentions alone (Gollwitzer & Sheeran, 2006)."),
        Lens("free", "Say it freely", listOf(
            "Anything on your mind that you haven't said to anyone?",
            "Say it without editing yourself.",
        ), "Honest, unpolished expression is what helps. Grammar and neatness made no difference in Pennebaker's expressive writing studies."),
    )

    /** One extra question per weekday, as in Dusk. */
    fun spotlight(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "What would make next week feel like a good week?"
        DayOfWeek.MONDAY -> "What are you putting off, and what is the smallest first step?"
        DayOfWeek.TUESDAY -> "When did you feel most like yourself today?"
        DayOfWeek.WEDNESDAY -> "What has drained you this week so far, and what refilled you?"
        DayOfWeek.THURSDAY -> "What did you learn today that you didn't know yesterday?"
        DayOfWeek.FRIDAY -> "What are you quietly proud of from this week?"
        DayOfWeek.SATURDAY -> "Imagine your best possible self a year from now. What would they notice about today?"
    }
}
