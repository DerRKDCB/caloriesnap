package com.example.caloriestracker.data

import org.json.JSONArray
import org.json.JSONObject

/** A single logged meal for the current day. */
data class Meal(
    val id: String,
    val calories: Int,
    val note: String,
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("calories", calories)
        put("note", note)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): Meal = Meal(
            id = json.optString("id"),
            calories = json.optInt("calories"),
            note = json.optString("note"),
            timestamp = json.optLong("timestamp")
        )

        fun listToJson(meals: List<Meal>): String {
            val array = JSONArray()
            meals.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(raw: String?): List<Meal> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        add(fromJson(array.getJSONObject(i)))
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
