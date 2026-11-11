package com.cerniera.ferro

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import com.cerniera.kit.Kit
import com.cerniera.kit.SECONDARY
import com.cerniera.kit.blend
import com.cerniera.kit.pressable
import com.cerniera.kit.px
import com.cerniera.kit.rounded
import com.cerniera.kit.text
import com.cerniera.kit.withAlpha
import kotlin.math.max
import kotlin.math.min

/**
 * A number with a minus and a plus either side, big enough to set with a thumb mid-workout. The
 * figure itself is the label: its unit sits small beneath it.
 */
@SuppressLint("ViewConstructor")
class Stepper(ctx: Context, unit: String, start: Double, private val step: Double, private val floor: Double = 0.0, private val changed: (Double) -> Unit = {}) : LinearLayout(ctx) {
    var value = start
        private set
    private val figure = ctx.text("", 38f, Kit.INK, displayFace = true).apply { gravity = Gravity.CENTER }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Kit.RAISED, ctx.px(22).toFloat(), Kit.HAIRLINE, ctx.px(1))
        setPadding(ctx.px(6), ctx.px(6), ctx.px(6), ctx.px(6))
        addView(button("−") { nudge(-step) }, LayoutParams(ctx.px(56), ctx.px(56)))
        val mid = LinearLayout(ctx).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
        mid.addView(figure)
        mid.addView(ctx.text(unit.uppercase(), 10.5f, Kit.MUTED, 500).apply { letterSpacing = 0.18f; gravity = Gravity.CENTER })
        addView(mid, LayoutParams(0, -2, 1f))
        addView(button("+") { nudge(step) }, LayoutParams(ctx.px(56), ctx.px(56)))
        show()
    }

    private fun button(s: String, onTap: () -> Unit) = context.text(s, 26f, Kit.CHAMPAGNE, 300).apply {
        gravity = Gravity.CENTER
        background = rounded(Kit.SURFACE, context.px(18).toFloat())
        isClickable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onTap() }
        pressable()
    }

    private fun nudge(d: Double) = set(max(floor, value + d))

    fun set(v: Double) {
        value = Math.round(v * 100) / 100.0
        show()
        // Membrane: the figure gives a little under the thumb and springs back
        figure.scaleX = 1.06f; figure.scaleY = 1.06f
        figure.animate().scaleX(1f).scaleY(1f).setDuration(Kit.SETTLE).setInterpolator(Kit.ease).start()
        changed(value)
    }

    private fun show() { figure.text = Coach.fmt(value) }
}

/**
 * An exercise's progress: the best estimated 1RM of each session, drawn as one continuous curve
 * through the points rather than a jagged line. New bests are marked in champagne. The curve draws
 * itself in from the left once, then holds still.
 */
class ProgressChart(ctx: Context) : View(ctx) {
    private var pts: List<Double> = emptyList()
    private var reveal = 1f
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val area = Path()

    fun show(values: List<Double>) {
        pts = values
        contentDescription = if (values.isEmpty()) "No sessions yet" else "Best estimated one-rep max over ${values.size} sessions, latest ${Coach.fmt1(values.last())} kilograms"
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700; interpolator = Kit.ease
            addUpdateListener { reveal = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), context.px(150))

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pad = context.px(10).toFloat()
        line.strokeWidth = context.px(1).toFloat()
        line.color = Kit.HAIRLINE
        for (i in 1..3) { val y = pad + (h - 2 * pad) * i / 4f; c.drawLine(pad, y, w - pad, y, line) }
        if (pts.size < 2) {
            fill.color = Kit.MUTED; fill.textSize = context.px(13).toFloat(); fill.typeface = Kit.sans(context)
            c.drawText(if (pts.isEmpty()) "Log a session to begin the curve" else "One session so far", pad, h / 2, fill)
            return
        }
        val lo = pts.min() * 0.97; val hi = pts.max() * 1.02
        fun x(i: Int) = pad + (w - 2 * pad) * i / (pts.size - 1).toFloat()
        fun y(v: Double) = (h - pad - (h - 2 * pad) * ((v - lo) / (hi - lo).coerceAtLeast(1e-6))).toFloat()
        path.reset(); path.moveTo(x(0), y(pts[0]))
        for (i in 1 until pts.size) {
            // Catmull-Rom through the points, as cubic Béziers: one continuous curve
            val p0 = pts[max(0, i - 2)]; val p1 = pts[i - 1]; val p2 = pts[i]; val p3 = pts[min(pts.size - 1, i + 1)]
            val x0 = x(max(0, i - 2)); val x1 = x(i - 1); val x2 = x(i); val x3 = x(min(pts.size - 1, i + 1))
            path.cubicTo(x1 + (x2 - x0) / 6f, y(p1) + (y(p2) - y(p0)) / 6f, x2 - (x3 - x1) / 6f, y(p2) - (y(p3) - y(p1)) / 6f, x2, y(p2))
        }
        area.set(path); area.lineTo(x(pts.size - 1), h); area.lineTo(x(0), h); area.close()
        c.save()
        c.clipRect(0f, 0f, pad + (w - 2 * pad) * reveal + pad, h)
        fill.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, withAlpha(Kit.CHAMPAGNE, 0.22f), withAlpha(Kit.CHAMPAGNE, 0f), android.graphics.Shader.TileMode.CLAMP)
        c.drawPath(area, fill); fill.shader = null
        line.color = Kit.CHAMPAGNE; line.strokeWidth = context.px(2.2f).toFloat()
        c.drawPath(path, line)
        var best = 0.0
        pts.forEachIndexed { i, v ->
            val isBest = v > best * (1 + 1e-9) && i > 0
            best = max(best, v)
            fill.color = if (isBest) Kit.CHAMPAGNE else Kit.GROUND
            c.drawCircle(x(i), y(v), context.px(if (isBest) 4f else 3f).toFloat(), fill)
            if (!isBest) { line.strokeWidth = context.px(1.5f).toFloat(); c.drawCircle(x(i), y(v), context.px(3f).toFloat(), line) }
        }
        c.restore()
    }
}

/**
 * The rest between sets: a thin champagne line that drains from full to nothing across the bar,
 * the seconds left beside it. While it runs, everything else on the page steps back (Concentrato).
 */
class RestBar(ctx: Context) : LinearLayout(ctx) {
    private val seconds = ctx.text("", 30f, Kit.INK, displayFace = true)
    private val label = ctx.text("REST", 10.5f, Kit.MUTED, 500).apply { letterSpacing = 0.2f; tag = SECONDARY }
    private var total = 1L
    private var endsAt = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var onDone: () -> Unit = {}
    var running = false
        private set

    val plus = ctx.text("+30 s", 14f, Kit.INK, 500).apply {
        setPadding(ctx.px(16), ctx.px(10), ctx.px(16), ctx.px(10)); background = rounded(Kit.RAISED, ctx.px(18).toFloat()); isClickable = true; pressable()
        setOnClickListener { endsAt += 30_000; total += 30_000 }
    }
    val skip = ctx.text("Skip", 14f, Kit.GROUND, 500).apply {
        setPadding(ctx.px(16), ctx.px(10), ctx.px(16), ctx.px(10)); background = rounded(Kit.CHAMPAGNE, ctx.px(18).toFloat()); isClickable = true; pressable()
        setOnClickListener { finish(false) }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        background = rounded(Kit.SURFACE, ctx.px(24).toFloat(), Kit.HAIRLINE, ctx.px(1))
        setPadding(ctx.px(20), ctx.px(14), ctx.px(14), ctx.px(18))
        val words = LinearLayout(ctx).apply { orientation = VERTICAL; addView(label); addView(seconds) }
        addView(words, LayoutParams(0, -2, 1f))
        addView(plus)
        addView(skip, LayoutParams(-2, -2).apply { marginStart = ctx.px(8) })
        visibility = GONE
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val left = endsAt - System.currentTimeMillis()
            if (left <= 0) { finish(true); return }
            val s = ((left + 999) / 1000).toInt()
            seconds.text = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "$s"
            invalidate()
            postDelayed(this, 200)
        }
    }

    fun start(sec: Int) {
        total = sec * 1000L
        endsAt = System.currentTimeMillis() + total
        running = true
        visibility = VISIBLE
        alpha = 0f; translationY = context.px(20).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(Kit.SETTLE).setInterpolator(Kit.ease).start()
        removeCallbacks(ticker); post(ticker)
    }

    fun finish(timeUp: Boolean) {
        if (!running) return
        running = false
        removeCallbacks(ticker)
        if (timeUp) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        animate().alpha(0f).setDuration(Kit.QUICK).withEndAction { visibility = GONE }.start()
        onDone()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (!running) return
        val f = ((endsAt - System.currentTimeMillis()).toFloat() / total).coerceIn(0f, 1f)
        val inset = context.px(20).toFloat()
        val y = height - context.px(8).toFloat()
        p.strokeWidth = context.px(2).toFloat(); p.strokeCap = Paint.Cap.ROUND
        p.color = Kit.HAIRLINE; c.drawLine(inset, y, width - inset, y, p)
        p.color = blend(Kit.WARN, Kit.CHAMPAGNE, f.coerceAtMost(0.3f) / 0.3f)
        c.drawLine(inset, y, inset + (width - 2 * inset) * f, y, p)
    }
}
