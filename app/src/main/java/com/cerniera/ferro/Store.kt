package com.cerniera.ferro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything Ferro knows, in one JSON file on the phone: the plan, every session and the
 * weigh-ins. Nothing leaves the phone except a backup you choose to share. Written to a temporary
 * file and renamed, so a crash mid-write never leaves a half file.
 */
class Store(private val c: Context) {
    var plan: Plan = Plan.starter(); private set
    var sessions: List<Session> = emptyList(); private set
    var weighs: List<Weigh> = emptyList(); private set
    /** A session in progress, if one was left open. */
    val open get() = sessions.firstOrNull { it.ended == null }
    val done get() = sessions.filter { it.ended != null }

    private val file get() = File(c.filesDir, "ferro.json")

    init { load() }

    private fun load() {
        val f = file
        if (!f.exists()) return
        runCatching { read(JSONObject(f.readText())) }
    }

    private fun save() {
        val tmp = File(c.filesDir, "ferro.json.tmp")
        tmp.writeText(write().toString())
        tmp.renameTo(file)
    }

    fun setPlan(p: Plan) { plan = p; save() }
    fun put(s: Session) { sessions = sessions.filter { it.id != s.id } + s; save() }
    fun remove(id: Long) { sessions = sessions.filter { it.id != id }; save() }
    fun weigh(w: Weigh) { weighs = (weighs.filter { it.date != w.date } + w).sortedBy { it.date }; save() }

    /** The whole record as JSON text, for a backup. */
    fun export(): String = write().toString(1)

    /** Replaces everything with a backup. Returns false and changes nothing if it can't be read. */
    fun restore(text: String): Boolean = runCatching { read(JSONObject(text)); save() }.isSuccess

    // ---- JSON --------------------------------------------------------------------------------

    private fun write() = JSONObject().apply {
        put("version", 1)
        put("plan", JSONArray().apply {
            plan.exercises.forEach { e ->
                put(JSONObject().put("id", e.id).put("name", e.name).put("day", e.day.name).put("sets", e.sets)
                    .put("lo", e.repLow).put("hi", e.repHigh).put("inc", e.increment).put("rest", e.restSec).put("order", e.order))
            }
        })
        put("sessions", JSONArray().apply {
            sessions.forEach { s ->
                put(JSONObject().put("id", s.id).put("day", s.day.name).put("start", s.started).apply { s.ended?.let { put("end", it) } }
                    .put("entries", JSONArray().apply {
                        s.entries.forEach { l ->
                            put(JSONObject().put("ex", l.exerciseId).put("sets", JSONArray().apply {
                                l.sets.forEach { put(JSONArray().put(it.kg).put(it.reps)) }
                            }))
                        }
                    }))
            }
        })
        put("weighs", JSONArray().apply { weighs.forEach { put(JSONObject().put("date", it.date).put("kg", it.kg)) } })
    }

    private fun read(o: JSONObject) {
        val p = o.getJSONArray("plan")
        val ex = (0 until p.length()).map { i ->
            val e = p.getJSONObject(i)
            Exercise(e.getString("id"), e.getString("name"), Day.valueOf(e.getString("day")), e.optInt("sets", 3),
                e.optInt("lo", 8), e.optInt("hi", 12), e.optDouble("inc", 2.5), e.optInt("rest", 120), e.optInt("order", i))
        }
        val ss = o.optJSONArray("sessions") ?: JSONArray()
        val sessions = (0 until ss.length()).map { i ->
            val s = ss.getJSONObject(i)
            val en = s.getJSONArray("entries")
            Session(s.getLong("id"), Day.valueOf(s.getString("day")), s.getLong("start"), if (s.has("end")) s.getLong("end") else null,
                (0 until en.length()).map { j ->
                    val l = en.getJSONObject(j); val sets = l.getJSONArray("sets")
                    Logged(l.getString("ex"), (0 until sets.length()).map { k -> val a = sets.getJSONArray(k); SetEntry(a.getDouble(0), a.getInt(1)) })
                })
        }
        val ws = o.optJSONArray("weighs") ?: JSONArray()
        val weighs = (0 until ws.length()).map { i -> val w = ws.getJSONObject(i); Weigh(w.getString("date"), w.getDouble("kg")) }
        plan = Plan(ex); this.sessions = sessions; this.weighs = weighs
    }
}
