package com.cerniera.ferro

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads the log the way a coach would after each session: what to lift next time, and what the
 * numbers say about plateaus, sets that fall away, fatigue and recovery. Plain Kotlin, no Android,
 * so it can be tested on its own.
 *
 * The thresholds are general strength-training rules of thumb, not a prescription; each is named
 * here so it can be tuned in one place.
 */
object Coach {
    /** Reps on the last set below this share of the first, at the same weight, is a drop-off. */
    const val DROP_OFF = 0.70
    /** A session's volume below this share of its usual is a fatigue signal. */
    const val FATIGUE = 0.85
    /** Best estimated 1RM must beat the earlier best by more than this to count as progress. */
    const val PROGRESS = 0.01
    /** Sessions without progress before it is called a plateau. */
    const val PLATEAU_SESSIONS = 3

    /** Estimated one-rep max (Epley). Bodyweight sets score by reps alone. */
    fun e1rm(s: SetEntry): Double = when {
        s.reps <= 0 -> 0.0
        s.kg <= 0.0 -> s.reps.toDouble()
        s.reps == 1 -> s.kg
        else -> s.kg * (1 + s.reps / 30.0)
    }

    fun best(sets: List<SetEntry>) = sets.maxOfOrNull { e1rm(it) } ?: 0.0

    /** The weight most sets were done at; on a tie, the heavier. */
    fun working(sets: List<SetEntry>): Double =
        sets.groupingBy { it.kg }.eachCount().entries.maxWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: 0.0

    /** Finished sessions that include [ex], oldest first. */
    fun history(ex: Exercise, sessions: List<Session>) =
        sessions.filter { it.ended != null && it.setsOf(ex.id).isNotEmpty() }.sortedBy { it.started }

    // ---- What to lift next ---------------------------------------------------------------

    data class Target(val kg: Double, val reps: Int, val why: String)

    fun next(ex: Exercise, sessions: List<Session>): Target? {
        val h = history(ex, sessions)
        val last = h.lastOrNull()?.setsOf(ex.id) ?: return null
        val kg = working(last)
        val atKg = last.filter { it.kg == kg }
        val done = atKg.size >= ex.sets
        if (done && atKg.all { it.reps >= ex.repHigh }) {
            val up = kg + ex.increment
            return Target(up, ex.repLow, "Every set reached ${ex.repHigh} at ${fmt(kg)} kg, so add ${fmt(ex.increment)} kg and start again at ${ex.repLow}.")
        }
        // Missed the bottom of the range two sessions running at this weight: come down a step
        val prev = h.dropLast(1).lastOrNull()?.setsOf(ex.id)
        val missed = atKg.any { it.reps < ex.repLow }
        val missedBefore = prev != null && working(prev) == kg && prev.filter { it.kg == kg }.any { it.reps < ex.repLow }
        if (missed && missedBefore && kg > 0.0) {
            val down = (kg - ex.increment).coerceAtLeast(0.0)
            return Target(down, ex.repLow + 1, "Below ${ex.repLow} reps at ${fmt(kg)} kg two sessions running: step back to ${fmt(down)} kg and build again.")
        }
        val low = atKg.minOfOrNull { it.reps } ?: ex.repLow
        val aim = (low + 1).coerceIn(ex.repLow, ex.repHigh)
        return Target(kg, aim, "Stay at ${fmt(kg)} kg and get every set to at least $aim reps.")
    }

    // ---- What the numbers say ------------------------------------------------------------

    enum class Kind { BEST, PLATEAU, DROP_OFF, WEIGHT_DROP, FATIGUE, RECOVERY, BALANCE, BODYWEIGHT }

    data class Flag(val kind: Kind, val title: String, val detail: String, val exerciseId: String? = null)

    /** Flags for one exercise as of its latest session. */
    fun flags(ex: Exercise, sessions: List<Session>): List<Flag> {
        val h = history(ex, sessions)
        if (h.isEmpty()) return emptyList()
        val out = mutableListOf<Flag>()
        val sets = h.last().setsOf(ex.id)
        val bests = h.map { best(it.setsOf(ex.id)) }

        // A new best
        if (h.size >= 2 && bests.last() > bests.dropLast(1).max() * (1 + 1e-9)) {
            val top = sets.maxBy { e1rm(it) }
            out += Flag(Kind.BEST, "New best on ${ex.name}", "${fmt(top.kg)} kg × ${top.reps}, an estimated 1RM of ${fmt1(bests.last())} kg.", ex.id)
        }

        // A plateau: the last few sessions never beat the best that came before them
        if (h.size > PLATEAU_SESSIONS) {
            val before = bests.dropLast(PLATEAU_SESSIONS).max()
            val recent = bests.takeLast(PLATEAU_SESSIONS).max()
            if (recent <= before * (1 + PROGRESS)) {
                out += Flag(Kind.PLATEAU, "${ex.name} has stalled",
                    "No progress in $PLATEAU_SESSIONS sessions (best estimated 1RM ${fmt1(recent)} kg against ${fmt1(before)} kg before). " +
                        "Try a lighter week at about 90% of the working weight, or move the rep range, then build again.", ex.id)
            }
        }

        // Reps falling away across sets at the same weight
        val kg = working(sets)
        val atKg = sets.filter { it.kg == kg }
        if (atKg.size >= 2) {
            val first = atKg.first().reps; val lastR = atKg.last().reps
            if (first > 0 && lastR < first * DROP_OFF) {
                out += Flag(Kind.DROP_OFF, "Reps fell away on ${ex.name}",
                    "From $first to $lastR across sets at ${fmt(kg)} kg. Rest longer between sets (try ${ex.restSec + 30} s), " +
                        "or take ${fmt(ex.increment)} kg off the last set.", ex.id)
            }
        }

        // The weight coming down after the first set
        if (sets.size >= 2 && sets.drop(1).any { it.kg < sets.first().kg }) {
            val lightest = sets.minOf { it.kg }
            out += Flag(Kind.WEIGHT_DROP, "Weight came down on ${ex.name}",
                "Started at ${fmt(sets.first().kg)} kg and finished at ${fmt(lightest)} kg. If the first set was a reach, " +
                    "start at the weight you can hold for every set.", ex.id)
        }
        return out
    }

    /** Volume of a session: kilograms × reps, with bodyweight sets counted by reps. */
    fun volume(s: Session) = s.entries.sumOf { l -> l.sets.sumOf { if (it.kg > 0) it.kg * it.reps else it.reps.toDouble() } }

    /** Flags for a finished session as a whole: fatigue and recovery. */
    fun sessionFlags(s: Session, sessions: List<Session>, plan: Plan): List<Flag> {
        val out = mutableListOf<Flag>()
        val same = sessions.filter { it.day == s.day && it.ended != null && it.started < s.started }.sortedBy { it.started }

        // Recovery: how long since this day was last trained
        same.lastOrNull()?.let { prev ->
            val days = ((s.started - prev.started) / 86_400_000.0)
            if (days < 2.0) out += Flag(Kind.RECOVERY, "Short recovery",
                "${s.day.label} again after ${hours(s.started - prev.started)}. The same muscles usually need about 48 hours.")
            else if (days > 9.0) out += Flag(Kind.RECOVERY, "A long gap",
                "${days.roundToInt()} days since the last ${s.day.label}. Ease back in; strength usually returns within a session or two.")
        }

        // Fatigue: volume well under the usual for this day, or several exercises fading
        val usual = same.takeLast(3).map { volume(it) }
        val v = volume(s)
        val fading = s.entries.count { l -> plan.exercises.firstOrNull { it.id == l.exerciseId }?.let { ex -> flags(ex, sessions.filter { it.started <= s.started }).any { it.kind == Kind.DROP_OFF } } == true }
        if (usual.size >= 2 && v < usual.average() * FATIGUE) {
            val pct = ((1 - v / usual.average()) * 100).roundToInt()
            out += Flag(Kind.FATIGUE, "Lighter than usual",
                "Volume $pct% below your recent ${s.day.label} sessions. Worth checking sleep, food and stress before pushing harder.")
        } else if (fading >= 3) {
            out += Flag(Kind.FATIGUE, "Fading through the session",
                "Reps fell away on $fading exercises. Longer rests or fewer exercises may get more out of the ones that matter.")
        }
        return out
    }

    // ---- Across the split ------------------------------------------------------------------

    /** The next day in the rotation, from the last finished session. */
    fun nextDay(sessions: List<Session>): Day {
        val last = sessions.filter { it.ended != null }.maxByOrNull { it.started } ?: return Day.PUSH
        return Day.entries[(last.day.ordinal + 1) % Day.entries.size]
    }

    /** Working sets per day over the last [days] days. */
    fun setsPerDay(sessions: List<Session>, now: Long, days: Int = 14): Map<Day, Int> {
        val from = now - days * 86_400_000L
        return Day.entries.associateWith { d -> sessions.filter { it.day == d && it.started >= from && it.ended != null }.sumOf { s -> s.entries.sumOf { it.sets.size } } }
    }

    /** Legs are the priority while bulking: flag when they get fewer sets than another day. */
    fun balance(sessions: List<Session>, now: Long): Flag? {
        val m = setsPerDay(sessions, now)
        val legs = m[Day.LEGS] ?: 0
        val most = m.filterKeys { it != Day.LEGS }.maxByOrNull { it.value } ?: return null
        if (most.value == 0 || legs >= most.value) return null
        return Flag(Kind.BALANCE, "Legs are behind",
            "$legs leg sets in the last fortnight against ${most.value} for ${most.key.label}. Legs are your priority this bulk.")
    }

    /** Kilograms a week from a straight line through the last four weeks of weigh-ins. */
    fun trend(weighs: List<Weigh>, epochDay: (String) -> Long): Double? {
        val pts = weighs.map { epochDay(it.date).toDouble() to it.kg }.sortedBy { it.first }
        if (pts.size < 3) return null
        val end = pts.last().first
        val w = pts.filter { it.first >= end - 28 }
        if (w.size < 3 || w.last().first - w.first().first < 7) return null
        val mx = w.sumOf { it.first } / w.size; val my = w.sumOf { it.second } / w.size
        val num = w.sumOf { (it.first - mx) * (it.second - my) }
        val den = w.sumOf { (it.first - mx) * (it.first - mx) }
        return if (den == 0.0) null else num / den * 7
    }

    /**
     * A bulk's pace, as a share of bodyweight a week. About 0.25 to 0.5% is a common general
     * guideline for lean gain; it is guidance, not a target the app enforces.
     */
    fun bulkFlag(weighs: List<Weigh>, epochDay: (String) -> Long): Flag? {
        val t = trend(weighs, epochDay) ?: return null
        val kg = weighs.maxBy { epochDay(it.date) }.kg
        val pct = t / kg * 100
        val rate = "${if (t >= 0) "+" else ""}${fmt1(t)} kg a week (${fmt1(pct)}% of bodyweight)"
        return when {
            t < 0 -> Flag(Kind.BODYWEIGHT, "Weight is going down", "$rate over the last four weeks. A bulk needs a little more food.")
            pct < 0.15 -> Flag(Kind.BODYWEIGHT, "Weight is flat", "$rate. Gaining slower than the usual 0.25–0.5% a week guideline.")
            pct > 0.75 -> Flag(Kind.BODYWEIGHT, "Gaining quickly", "$rate. Faster than the usual 0.25–0.5% a week guideline; more of it may be fat.")
            else -> Flag(Kind.BODYWEIGHT, "On pace", "$rate, inside the usual 0.25–0.5% a week guideline.")
        }
    }

    // ---- Formatting ----------------------------------------------------------------------

    fun fmt(kg: Double): String = if (abs(kg - kg.roundToInt()) < 1e-6) kg.roundToInt().toString() else "%.2f".format(kg).trimEnd('0').trimEnd('.')
    fun fmt1(v: Double): String = "%.1f".format(v)
    private fun hours(ms: Long) = "${(ms / 3_600_000.0).roundToInt()} hours"
}
