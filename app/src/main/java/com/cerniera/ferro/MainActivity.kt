package com.cerniera.ferro

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.cerniera.kit.Card
import com.cerniera.kit.Crash
import com.cerniera.kit.Fold
import com.cerniera.kit.Hinge
import com.cerniera.kit.Kit
import com.cerniera.kit.Row
import com.cerniera.kit.SECONDARY
import com.cerniera.kit.Screen
import com.cerniera.kit.Segmented
import com.cerniera.kit.Sheet
import com.cerniera.kit.doctrineCard
import com.cerniera.kit.endValue
import com.cerniera.kit.field
import com.cerniera.kit.figure
import com.cerniera.kit.primary
import com.cerniera.kit.px
import com.cerniera.kit.rounded
import com.cerniera.kit.text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ferro: the training log. Folded, it is a one-handed logger: the exercise in hand, its weight and
 * reps, Log set, and the rest that follows. Unfolded, the coach opens beside it: the exercise's
 * curve, what to lift next time and why, and anything the numbers are saying.
 */
class MainActivity : Activity() {
    private lateinit var store: Store
    private val rest by lazy { RestBar(this) }
    private var mode = Mode.HOME
    private var current: String? = null
    private var summaryId: Long? = null

    enum class Mode { HOME, SESSION, SUMMARY }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (Crash.show(this, "Ferro")) return
        Kit.follow(this)
        store = Store(this)
        current = saved?.getString("current")
        mode = if (store.open != null) Mode.SESSION else Mode.HOME
        intent?.getStringExtra("screen")?.let { if (it == "session" && store.open == null) begin(Coach.nextDay(store.sessions), show = false) }
        rest.onDone = { render() }
        render()
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("current", current) }

    /** The fold and the light change in place, so a rest that is running keeps running. */
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        Kit.follow(this)
        render()
    }

    @Deprecated("Back closes a sheet-free screen the usual way")
    override fun onBackPressed() {
        when (mode) {
            Mode.SUMMARY -> { mode = Mode.HOME; render() }
            Mode.SESSION -> { mode = Mode.HOME; render() }
            Mode.HOME -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    // ---- Layout ------------------------------------------------------------------------------

    private fun render() {
        val open = store.open
        if (open != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Kit.GROUND; window.navigationBarColor = Kit.GROUND
        val root = FrameLayout(this).apply { setBackgroundColor(Kit.GROUND) }
        val wide = Hinge.unfolded(this)
        val content: View = when {
            mode == Mode.SESSION && open != null -> if (wide) twoPane(session(open).root, coach(open).root) else session(open).root
            mode == Mode.SUMMARY && summaryId != null -> summary(store.sessions.first { it.id == summaryId }).root
            else -> home().root
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        (rest.parent as? FrameLayout)?.removeView(rest)
        root.addView(rest, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels - px(32), px(560)), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(24) })
        setContentView(root)
        concentrato()
    }

    private fun twoPane(left: View, right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, -1, 1f))
        // The crease runs between them: a hairline where the hardware folds
        addView(View(context).apply { setBackgroundColor(Kit.HAIRLINE) }, LinearLayout.LayoutParams(px(1), -1))
        addView(right, LinearLayout.LayoutParams(0, -1, 1f))
    }

    /** While resting, everything but the rest and the exercise in hand steps back. */
    private fun concentrato() {
        val content = (rest.parent as? FrameLayout)?.getChildAt(0) ?: return
        fun walk(v: View) {
            if (v.tag == SECONDARY) v.animate().alpha(if (rest.running) 0.25f else 1f).setDuration(Kit.SETTLE).start()
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(content)
    }

    // ---- Home --------------------------------------------------------------------------------

    private fun home(): Screen {
        val s = Screen(this, "Ferro", "Cerniera · Training")
        val now = System.currentTimeMillis()
        val open = store.open
        val next = open?.day ?: Coach.nextDay(store.sessions)
        var pick = next

        // The next session
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Kit.SURFACE, px(26).toFloat(), Kit.HAIRLINE, px(1))
            setPadding(px(22), px(20), px(22), px(22))
        }
        val dayName = text(next.label, 52f, Kit.INK, displayFace = true)
        val dayNote = text("", 14f, Kit.MUTED).apply { tag = SECONDARY }
        fun describe(d: Day) {
            dayName.text = d.label
            val last = store.done.filter { it.day == d }.maxByOrNull { it.started }
            val ex = store.plan.of(d)
            dayNote.text = (if (last == null) "Not trained yet" else "Last ${since(last.started, now)}") + " · ${ex.size} exercises, ${ex.sumOf { it.sets }} sets"
        }
        describe(next)
        card.addView(text(if (open != null) "IN PROGRESS" else "NEXT", 11f, Kit.CHAMPAGNE, 500).apply { letterSpacing = 0.22f })
        card.addView(dayName)
        card.addView(dayNote, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(2) })
        if (open == null) {
            card.addView(Segmented(this, Day.entries.map { it.label }, next.ordinal) { pick = Day.entries[it]; describe(pick) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
            card.addView(primary("Begin") { begin(pick) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })
        } else {
            card.addView(primary("Resume ${open.day.label}") { mode = Mode.SESSION; render() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
        }
        s.add(card, px(22))

        // What the numbers say across the split
        val notes = buildList {
            Coach.balance(store.sessions, now)?.let { add(it) }
            Coach.bulkFlag(store.weighs, ::epochDay)?.let { add(it) }
            store.done.maxByOrNull { it.started }?.let { last ->
                addAll(Coach.sessionFlags(last, store.sessions, store.plan))
                last.entries.forEach { l -> store.plan.exercises.firstOrNull { it.id == l.exerciseId }?.let { addAll(Coach.flags(it, store.sessions)) } }
            }
        }
        if (notes.isNotEmpty()) {
            s.section("The coach")
            s.add(flagCard(notes.take(5)))
        }

        // Bodyweight
        s.section("Bodyweight")
        val wc = Card(this)
        val lastW = store.weighs.lastOrNull()
        val trend = Coach.trend(store.weighs, ::epochDay)
        wc.row(Row(this, if (lastW == null) "No weigh-ins yet" else "${Coach.fmt(lastW.kg)} kg",
            if (lastW == null) "Weigh in once or twice a week, same time of day" else "${pretty(lastW.date)}${trend?.let { " · ${if (it >= 0) "+" else ""}${Coach.fmt1(it)} kg a week" } ?: ""}",
            endValue("Log", Kit.CHAMPAGNE)) { weighIn() })
        s.add(wc)

        // History
        if (store.done.isNotEmpty()) {
            s.section("Sessions")
            val hc = Card(this)
            store.done.sortedByDescending { it.started }.take(10).forEach { x ->
                val sets = x.entries.sumOf { it.sets.size }
                hc.row(Row(this, "${x.day.label} · ${pretty(x.started)}", "$sets sets · ${Coach.fmt(Coach.volume(x))} kg moved", endValue("›")) {
                    summaryId = x.id; mode = Mode.SUMMARY; render()
                })
            }
            s.add(hc)
        }

        // The plan
        s.section("The plan")
        Day.entries.forEach { d ->
            val f = Fold(this, d.label, store.plan.of(d).joinToString(", ") { it.name })
            store.plan.of(d).forEach { e ->
                f.body.addView(Row(this, e.name, "${e.sets} × ${e.repLow}–${e.repHigh} · +${Coach.fmt(e.increment)} kg · ${e.restSec} s rest", endValue("Edit")) { editExercise(e, d) }.apply {
                    setPadding(0, px(12), 0, px(12))
                })
            }
            f.body.addView(text("Add an exercise", 15f, Kit.CHAMPAGNE, 500).apply {
                setPadding(0, px(14), 0, px(4)); isClickable = true; setOnClickListener { editExercise(null, d) }
            })
            s.add(f, px(10))
        }

        // Backup
        s.section("Your record")
        val bc = Card(this)
        bc.row(Row(this, "Back up", "Save everything as a file you keep", endValue("›")) { exportBackup() })
        bc.row(Row(this, "Restore", "Replace this record with a backup", endValue("›")) { importBackup() })
        s.add(bc)

        s.add(doctrineCard(listOf(
            "Membrane" to "The figures give a little under your thumb and spring back. Fling the page and the cards furthest from the middle lag, then settle.",
            "Concentrato" to "While you rest, everything but the rest and the exercise in hand steps back. Flinging the page drops the small print.",
            "Giulietta" to "A logged set's remove control appears only when you touch the set, and goes 3.2 seconds later.",
            "The fold" to "Folded, Ferro is a one-handed logger. Unfolded, the coach opens beside it with the curve, the next target and the reasons.",
        )), px(28))
        return s
    }

    private fun flagCard(flags: List<Coach.Flag>) = Card(this).apply {
        flags.forEach { f ->
            val colour = when (f.kind) {
                Coach.Kind.BEST -> Kit.GOOD
                Coach.Kind.PLATEAU, Coach.Kind.FATIGUE, Coach.Kind.RECOVERY, Coach.Kind.BALANCE -> Kit.WARN
                else -> Kit.CHAMPAGNE
            }
            val v = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(px(18), px(14), px(18), px(14))
                addView(View(context).apply { background = rounded(colour, 100f) }, LinearLayout.LayoutParams(px(7), px(7)).apply { topMargin = px(7); marginEnd = px(12) })
                val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                words.addView(text(f.title, 15.5f, Kit.INK, 500))
                words.addView(text(f.detail, 13.5f, Kit.MUTED), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(3) })
                addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            }
            row(v)
        }
    }

    // ---- Session -----------------------------------------------------------------------------

    private fun begin(d: Day, show: Boolean = true) {
        val s = Session(System.currentTimeMillis(), d, System.currentTimeMillis())
        store.put(s)
        current = store.plan.of(d).firstOrNull()?.id
        mode = Mode.SESSION
        if (show) render()
    }

    private fun session(open: Session): Screen {
        val s = Screen(this, open.day.label, "Ferro · ${since(open.started, System.currentTimeMillis(), short = true)}")
        val exercises = store.plan.of(open.day)
        if (current == null || exercises.none { it.id == current }) current = exercises.firstOrNull { open.setsOf(it.id).size < it.sets }?.id ?: exercises.firstOrNull()?.id
        val done = exercises.count { open.setsOf(it.id).size >= it.sets }
        s.status.set("$done of ${exercises.size}", if (done == exercises.size) Kit.GOOD else Kit.CHAMPAGNE)
        val wide = Hinge.unfolded(this)

        exercises.forEach { e ->
            val sets = open.setsOf(e.id)
            if (e.id == current) {
                s.add(exerciseCard(open, e, sets, chart = !wide), px(14))
            } else {
                val c = Card(this)
                val last = Coach.history(e, store.sessions.filter { it.id != open.id }).lastOrNull()?.setsOf(e.id)
                val sub = if (sets.isNotEmpty()) sets.joinToString("  ") { "${Coach.fmt(it.kg)}×${it.reps}" }
                    else "${e.sets} × ${e.repLow}–${e.repHigh}" + (last?.let { " · last ${Coach.fmt(Coach.working(it))} kg" } ?: "")
                c.row(Row(this, e.name, sub, endValue(if (sets.size >= e.sets) "✓" else "${sets.size}/${e.sets}", if (sets.size >= e.sets) Kit.GOOD else Kit.MUTED)) {
                    current = e.id; render()
                })
                s.add(c, px(10))
            }
        }
        s.add(primary("Finish session", filled = false) { finish(open) }, px(22))
        s.add(text("Leave it open and come back any time: the session waits.", 13f, Kit.MUTED).apply { gravity = Gravity.CENTER; tag = SECONDARY }, px(10))
        s.add(View(this), px(120))
        return s
    }

    private fun exerciseCard(open: Session, e: Exercise, sets: List<SetEntry>, chart: Boolean): View {
        val history = store.sessions.filter { it.id != open.id }
        val target = Coach.next(e, history)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Kit.SURFACE, px(26).toFloat(), Kit.CHAMPAGNE, px(1.5f))
            setPadding(px(20), px(18), px(20), px(20))
            animateLayoutChanges()
        }
        card.addView(text(e.name, 30f, Kit.INK, displayFace = true))
        card.addView(text(target?.let { "Today ${Coach.fmt(it.kg)} kg × ${it.reps}" } ?: "First time: choose a weight you could lift ${e.repHigh - 2} times cleanly",
            14.5f, Kit.CHAMPAGNE, 500), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
        target?.let { card.addView(text(it.why, 13f, Kit.MUTED).apply { tag = SECONDARY }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(3) }) }

        // Logged sets: tap one to reveal its remove control (Giulietta)
        val logged = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sets.forEachIndexed { i, st ->
            val chip = text("${Coach.fmt(st.kg)} × ${st.reps}", 14f, Kit.INK, 500).apply {
                setPadding(px(12), px(8), px(12), px(8)); background = rounded(Kit.RAISED, px(14).toFloat())
            }
            arm(chip, "${Coach.fmt(st.kg)} × ${st.reps}") { removeSet(open, e, i) }
            logged.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = px(8) })
        }
        if (sets.isNotEmpty()) card.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(logged) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })

        val startKg = sets.lastOrNull()?.kg ?: target?.kg ?: 20.0
        val startReps = sets.lastOrNull()?.reps ?: target?.reps ?: e.repLow
        val kg = Stepper(this, "kilograms", startKg, e.increment)
        val reps = Stepper(this, "reps", startReps.toDouble(), 1.0)
        // Stacked, each the card's full width: room for the figure and two thumb-sized buttons on the cover screen
        val steppers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        steppers.addView(kg, LinearLayout.LayoutParams(-1, -2))
        steppers.addView(reps, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        card.addView(steppers, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(16) })

        val label = if (sets.size >= e.sets) "Log an extra set" else "Log set ${sets.size + 1} of ${e.sets}"
        card.addView(primary(label) { logSet(open, e, SetEntry(kg.value, reps.value.roundToInt())) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })

        if (chart) {
            val pc = ProgressChart(this)
            card.addView(text("THE CURVE", 10.5f, Kit.MUTED, 500).apply { letterSpacing = 0.2f; tag = SECONDARY }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
            card.addView(pc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
            pc.show(Coach.history(e, history).map { Coach.best(it.setsOf(e.id)) })
        }
        return card
    }

    /** A logged set shows its remove control only once touched, and hides it again 3.2 s later. */
    private fun arm(chip: TextView, label: String, remove: () -> Unit) {
        chip.text = label; chip.setTextColor(Kit.INK)
        chip.setOnClickListener {
            chip.text = "Remove"; chip.setTextColor(Kit.ROSSO)
            chip.setOnClickListener { remove() }
            chip.postDelayed({ if (chip.isAttachedToWindow) arm(chip, label, remove) }, Kit.RECEDE_MS)
        }
    }

    private fun LinearLayout.animateLayoutChanges() { layoutTransition = android.animation.LayoutTransition() }

    /** The coach, beside the logger when unfolded: the curve, the next target and the flags. */
    private fun coach(open: Session): Screen {
        val e = store.plan.exercises.firstOrNull { it.id == current } ?: return Screen(this, "Coach", "Ferro")
        val s = Screen(this, "Coach", e.name)
        val history = store.sessions.filter { it.id != open.id }
        val h = Coach.history(e, history)
        val pc = ProgressChart(this)
        val chartCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Kit.SURFACE, px(26).toFloat(), Kit.HAIRLINE, px(1))
            setPadding(px(18), px(18), px(18), px(14))
        }
        val figures = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bestEver = h.maxOfOrNull { Coach.best(it.setsOf(e.id)) }
        figures.addView(figure(bestEver?.let { Coach.fmt1(it) } ?: "—", "best est. 1RM kg"), LinearLayout.LayoutParams(0, -2, 1f))
        figures.addView(figure("${h.size}", "sessions"), LinearLayout.LayoutParams(0, -2, 1f))
        chartCard.addView(figures)
        chartCard.addView(pc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        s.add(chartCard, px(18))
        pc.show(h.map { Coach.best(it.setsOf(e.id)) })

        Coach.next(e, history)?.let { t ->
            s.section("Today")
            s.add(Card(this).apply { row(Row(this@MainActivity, "${Coach.fmt(t.kg)} kg × ${t.reps}", t.why)) })
        }
        val flags = Coach.flags(e, history)
        if (flags.isNotEmpty()) { s.section("What the numbers say"); s.add(flagCard(flags)) }
        if (h.isNotEmpty()) {
            s.section("Recent")
            val c = Card(this)
            h.takeLast(6).reversed().forEach { x ->
                c.row(Row(this, pretty(x.started), x.setsOf(e.id).joinToString("   ") { "${Coach.fmt(it.kg)}×${it.reps}" }))
            }
            s.add(c)
        }
        return s
    }

    private fun logSet(open: Session, e: Exercise, set: SetEntry) {
        if (set.reps <= 0) return
        val cur = store.open ?: open
        val entries = cur.entries.toMutableList()
        val i = entries.indexOfFirst { it.exerciseId == e.id }
        if (i >= 0) entries[i] = Logged(e.id, entries[i].sets + set) else entries += Logged(e.id, listOf(set))
        store.put(cur.copy(entries = entries))
        val n = entries.first { it.exerciseId == e.id }.sets.size
        // The exercise is done: carry on to the next one that isn't
        if (n >= e.sets) store.plan.of(cur.day).firstOrNull { x -> (store.open?.setsOf(x.id)?.size ?: 0) < x.sets }?.let { current = it.id }
        render()
        rest.start(e.restSec)
        concentrato()
    }

    private fun removeSet(open: Session, e: Exercise, index: Int) {
        val cur = store.open ?: open
        val entries = cur.entries.map { if (it.exerciseId == e.id) Logged(e.id, it.sets.filterIndexed { j, _ -> j != index }) else it }.filter { it.sets.isNotEmpty() }
        store.put(cur.copy(entries = entries))
        render()
    }

    private fun finish(open: Session) {
        rest.finish(false)
        val cur = store.open ?: open
        if (cur.entries.isEmpty()) { store.remove(cur.id); mode = Mode.HOME; render(); return }
        store.put(cur.copy(ended = System.currentTimeMillis()))
        summaryId = cur.id; mode = Mode.SUMMARY
        render()
    }

    // ---- Summary -----------------------------------------------------------------------------

    private fun summary(x: Session): Screen {
        val s = Screen(this, x.day.label, "Ferro · ${pretty(x.started)}")
        val figures = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val mins = (((x.ended ?: x.started) - x.started) / 60_000.0).roundToInt()
        figures.addView(figure(Coach.fmt(Coach.volume(x)), "kg moved"), LinearLayout.LayoutParams(0, -2, 1f))
        figures.addView(figure("${x.entries.sumOf { it.sets.size }}", "sets"), LinearLayout.LayoutParams(0, -2, 1f))
        figures.addView(figure("$mins", "minutes"), LinearLayout.LayoutParams(0, -2, 1f))
        s.add(figures, px(18))

        val upTo = store.sessions.filter { it.started <= x.started }
        val flags = Coach.sessionFlags(x, store.sessions, store.plan) +
            x.entries.flatMap { l -> store.plan.exercises.firstOrNull { it.id == l.exerciseId }?.let { Coach.flags(it, upTo) }.orEmpty() }
        if (flags.isNotEmpty()) { s.section("What the numbers say"); s.add(flagCard(flags)) }

        s.section("Next time")
        val c = Card(this)
        x.entries.forEach { l ->
            val e = store.plan.exercises.firstOrNull { it.id == l.exerciseId } ?: return@forEach
            val t = Coach.next(e, upTo)
            c.row(Row(this, e.name, (l.sets.joinToString("  ") { "${Coach.fmt(it.kg)}×${it.reps}" }) + (t?.let { "\nNext: ${Coach.fmt(it.kg)} kg × ${it.reps}" } ?: "")))
        }
        s.add(c)
        s.add(primary("Done") { mode = Mode.HOME; render() }, px(22))
        s.add(text("Delete this session", 14f, Kit.ROSSO, 500).apply {
            gravity = Gravity.CENTER; setPadding(0, px(18), 0, px(8)); isClickable = true
            setOnClickListener { confirmDelete(x) }
        })
        return s
    }

    private fun confirmDelete(x: Session) = Sheet.show(this, "Delete session?") { sh ->
        sh.body.addView(text("${x.day.label} on ${pretty(x.started)} and its ${x.entries.sumOf { it.sets.size }} sets will be gone for good.", 14.5f, Kit.MUTED))
        sh.body.addView(primary("Delete") { store.remove(x.id); sh.dismiss(); mode = Mode.HOME; render() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
        sh.body.addView(primary("Keep it", filled = false) { sh.dismiss() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
    }

    // ---- Sheets ------------------------------------------------------------------------------

    private fun weighIn() {
        val f = field("Kilograms", store.weighs.lastOrNull()?.kg?.let { Coach.fmt(it) } ?: "", decimal = true)
        Sheet.show(this, "Weigh in", action = "Save" to { sh ->
            f.text.toString().replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }?.let { store.weigh(Weigh(LocalDate.now().toString(), it)) }
            sh.dismiss(); render()
        }) { sh ->
            sh.body.addView(text("Same time of day each time, ideally first thing.", 14f, Kit.MUTED).apply { setPadding(0, 0, 0, px(14)) })
            sh.body.addView(f)
        }
    }

    private fun editExercise(e: Exercise?, d: Day) {
        val name = field("Name", e?.name ?: "")
        val sets = field("Sets", "${e?.sets ?: 3}", numeric = true)
        val lo = field("Reps from", "${e?.repLow ?: 8}", numeric = true)
        val hi = field("Reps to", "${e?.repHigh ?: 12}", numeric = true)
        val inc = field("Add when ready (kg)", Coach.fmt(e?.increment ?: 2.5), decimal = true)
        val rest = field("Rest (seconds)", "${e?.restSec ?: 120}", numeric = true)
        Sheet.show(this, e?.name ?: "New exercise", action = "Save" to { sh ->
            val n = name.text.toString().trim()
            if (n.isNotEmpty()) {
                val l = lo.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: 8
                val h = (hi.text.toString().toIntOrNull() ?: 12).coerceIn(l, 60)
                val ex = Exercise(e?.id ?: "e${System.currentTimeMillis()}", n, d, sets.text.toString().toIntOrNull()?.coerceIn(1, 10) ?: 3, l, h,
                    inc.text.toString().replace(',', '.').toDoubleOrNull()?.coerceIn(0.25, 50.0) ?: 2.5,
                    rest.text.toString().toIntOrNull()?.coerceIn(15, 600) ?: 120, e?.order ?: (store.plan.of(d).maxOfOrNull { it.order } ?: 0) + 1)
                store.setPlan(Plan(store.plan.exercises.filter { it.id != ex.id } + ex))
                sh.dismiss(); render()
            }
        }) { sh -> fillExercise(sh, e, d, name, sets, lo, hi, inc, rest) }
    }

    private fun fillExercise(sh: Sheet, e: Exercise?, d: Day, name: View, sets: View, lo: View, hi: View, inc: View, rest: View) {
        fun pair(a: View, b: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a, LinearLayout.LayoutParams(0, -2, 1f)); addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(10) })
        }
        sh.body.addView(name)
        sh.body.addView(pair(sets, rest), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        sh.body.addView(pair(lo, hi), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        sh.body.addView(inc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        if (e != null) sh.body.addView(primary("Remove from ${d.label}", filled = false) {
            store.setPlan(Plan(store.plan.exercises.filter { it.id != e.id })); sh.dismiss(); render()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
    }

    // ---- Backup ------------------------------------------------------------------------------

    private fun exportBackup() = startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/json").putExtra(Intent.EXTRA_TITLE, "ferro-${LocalDate.now()}.json"), 1)

    private fun importBackup() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 2)

    @Deprecated("Fine for two one-off document picks")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        runCatching {
            if (req == 1) contentResolver.openOutputStream(uri)?.use { it.write(store.export().toByteArray()) }
            if (req == 2) {
                val txt = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                val ok = store.restore(txt)
                Sheet.show(this, if (ok) "Restored" else "Couldn't read that file") { sh ->
                    sh.body.addView(text(if (ok) "${store.done.size} sessions and ${store.weighs.size} weigh-ins are back." else "Nothing was changed. Choose a Ferro backup (.json).", 14.5f, Kit.MUTED))
                    sh.body.addView(primary("OK") { sh.dismiss() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(16) })
                }
            }
        }
        render()
    }

    // ---- Dates -------------------------------------------------------------------------------

    private fun epochDay(s: String) = runCatching { LocalDate.parse(s).toEpochDay() }.getOrDefault(0L)

    private fun pretty(ms: Long): String = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    private fun pretty(date: String): String = runCatching { DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK).format(LocalDate.parse(date)) }.getOrDefault(date)

    private fun since(ms: Long, now: Long, short: Boolean = false): String {
        val m = ((now - ms) / 60_000).toInt()
        return when {
            short && m < 60 -> "$m min"
            short -> "${m / 60} h ${m % 60} min"
            m < 60 * 24 -> "today"
            m < 60 * 48 -> "yesterday"
            else -> "${m / (60 * 24)} days ago"
        }
    }
}
