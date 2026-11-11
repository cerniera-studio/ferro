package com.cerniera.ferro

/** The split's three days. */
enum class Day(val label: String) { PUSH("Push"), PULL("Pull"), LEGS("Legs") }

/**
 * One exercise in the plan. Progression is double progression: work in the rep range at a weight,
 * and when every working set reaches the top of the range, the weight goes up by [increment].
 */
data class Exercise(
    val id: String,
    val name: String,
    val day: Day,
    val sets: Int = 3,
    val repLow: Int = 8,
    val repHigh: Int = 12,
    val increment: Double = 2.5,
    val restSec: Int = 120,
    val order: Int = 0,
)

/** One working set: kilograms and reps. Bodyweight work is logged as 0 kg. */
data class SetEntry(val kg: Double, val reps: Int)

data class Logged(val exerciseId: String, val sets: List<SetEntry>)

data class Session(
    val id: Long,
    val day: Day,
    val started: Long,
    val ended: Long? = null,
    val entries: List<Logged> = emptyList(),
) {
    fun setsOf(exerciseId: String) = entries.firstOrNull { it.exerciseId == exerciseId }?.sets.orEmpty()
}

data class Weigh(val date: String, val kg: Double)

data class Plan(val exercises: List<Exercise>) {
    fun of(day: Day) = exercises.filter { it.day == day }.sortedBy { it.order }

    companion object {
        /**
         * A starting push/pull/legs plan, leg-heavy for the bulk. Every exercise, set count, rep
         * range and rest can be changed in the app; this is only where it begins.
         */
        fun starter(): Plan {
            var n = 0
            fun e(day: Day, name: String, sets: Int, lo: Int, hi: Int, inc: Double, rest: Int) =
                Exercise("e${n++}", name, day, sets, lo, hi, inc, rest, n)
            return Plan(listOf(
                e(Day.PUSH, "Bench press", 3, 6, 10, 2.5, 150),
                e(Day.PUSH, "Overhead press", 3, 6, 10, 2.5, 150),
                e(Day.PUSH, "Incline dumbbell press", 3, 8, 12, 2.0, 120),
                e(Day.PUSH, "Lateral raise", 3, 12, 15, 1.0, 75),
                e(Day.PUSH, "Triceps pushdown", 3, 10, 15, 2.5, 75),
                e(Day.PULL, "Lat pulldown", 3, 8, 12, 2.5, 120),
                e(Day.PULL, "Barbell row", 3, 6, 10, 2.5, 150),
                e(Day.PULL, "Seated cable row", 3, 8, 12, 2.5, 120),
                e(Day.PULL, "Face pull", 3, 12, 15, 2.5, 75),
                e(Day.PULL, "Biceps curl", 3, 8, 12, 1.0, 75),
                e(Day.LEGS, "Back squat", 4, 5, 8, 5.0, 180),
                e(Day.LEGS, "Romanian deadlift", 3, 6, 10, 5.0, 150),
                e(Day.LEGS, "Leg press", 3, 10, 15, 10.0, 120),
                e(Day.LEGS, "Leg curl", 3, 10, 15, 2.5, 90),
                e(Day.LEGS, "Walking lunge", 3, 10, 12, 2.0, 90),
                e(Day.LEGS, "Standing calf raise", 4, 10, 15, 5.0, 60),
            ))
        }
    }
}
