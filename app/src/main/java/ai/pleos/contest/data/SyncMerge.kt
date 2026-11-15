package ai.pleos.contest.data

import org.json.JSONArray
import org.json.JSONObject

/** Three-way merge: disjoint fields survive, simultaneous same-field edits use this upload. */
object SyncMerge {
    fun equal(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keySet() == b.keySet() && a.keySet().all { equal(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { equal(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal()) == 0
        else -> a == b
    }
    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

    fun merge(base: JSONObject, local: JSONObject, remote: JSONObject): JSONObject {
        val result = JSONObject(remote.toString())
        for (key in base.keySet() + local.keySet()) {
            val before = base.opt(key); val after = local.opt(key)
            if (equal(before, after)) continue
            if (!local.has(key)) result.remove(key)
            else if (key in setOf("items", "observations") && before is JSONArray && after is JSONArray) {
                result.put(key, mergeRows(before, after, remote.optJSONArray(key) ?: JSONArray()))
            } else if (before is JSONObject && after is JSONObject && remote.opt(key) is JSONObject) {
                result.put(key, merge(before, after, remote.getJSONObject(key)))
            } else result.put(key, after)
        }
        return result
    }

    private fun mergeRows(base: JSONArray, local: JSONArray, remote: JSONArray): JSONArray {
        fun rows(a: JSONArray): Map<String, JSONObject> = (0 until a.length()).associate {
            val row = a.getJSONObject(it); row.getString("id") to row
        }
        val old = rows(base); val mine = rows(local); val theirs = rows(remote)
        val result = JSONArray()
        for ((id, row) in theirs) {
            if (id in old && id !in mine) continue // Explicit local deletion wins over a concurrent edit.
            val merged = if (id in old && id in mine) merge(old.getValue(id), mine.getValue(id), row) else row
            if (id in old && id in mine && row.has("loaded")) {
                val before = old.getValue(id); val localRow = mine.getValue(id)
                val renamedLocally = listOf("name", "place").any { !equal(before.opt(it), localRow.opt(it)) }
                val renamedRemotely = listOf("name", "place").any { !equal(before.opt(it), row.opt(it)) }
                if ((renamedLocally && !equal(before.opt("loaded"), row.opt("loaded"))) ||
                    (renamedRemotely && !equal(before.opt("loaded"), localRow.opt("loaded")))) merged.put("loaded", false)
            }
            result.put(merged)
        }
        for ((id, row) in mine) if (id !in theirs && id !in old) result.put(row)
        return result
    }
}
