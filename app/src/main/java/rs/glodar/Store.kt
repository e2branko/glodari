package rs.glodar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Ev(val t: Long, val d: Double, val f: Double, val sq: Boolean)

object State {
    @Volatile var running = false
    @Volatile var level = 0.0
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("glodar", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<Ev> {
        val a = JSONArray(p(c).getString("ev", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Ev(o.getLong("t"), o.getDouble("d"), o.getDouble("f"), o.getBoolean("sq"))
        }
    }

    @Synchronized
    fun add(c: Context, e: Ev) {
        var a = JSONArray(p(c).getString("ev", "[]"))
        a.put(JSONObject().put("t", e.t).put("d", e.d).put("f", e.f).put("sq", e.sq))
        if (a.length() > 2000) {
            val n = JSONArray()
            for (i in a.length() - 2000 until a.length()) n.put(a.get(i))
            a = n
        }
        p(c).edit().putString("ev", a.toString()).apply()
    }

    @Synchronized
    fun clear(c: Context) = p(c).edit().putString("ev", "[]").apply()

    fun threshold(c: Context): Int = p(c).getInt("thr", 8)
    fun setThreshold(c: Context, v: Int) = p(c).edit().putInt("thr", v).apply()
}
