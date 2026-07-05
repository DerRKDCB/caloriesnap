package com.example.caloriestracker.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

private val Context.calorieDataStore by preferencesDataStore(name = "calorie_prefs")

data class CaloriePreferences(
    val apiKey: String = "",
    val ollamaAddress: String = DEFAULT_OLLAMA_ADDRESS,
    val ollamaModel: String = DEFAULT_OLLAMA_MODEL,
    val dailyGoal: Int = 2000,
    val meals: List<Meal> = emptyList(),
    val todaysTotal: Int = 0,
    val todaysDate: LocalDate = LocalDate.now(),
    val mealHistory: Map<LocalDate, List<Meal>> = emptyMap()
) {
    companion object {
        const val DEFAULT_OLLAMA_ADDRESS = "http://localhost:11434"
        const val DEFAULT_OLLAMA_MODEL = "llava"
    }
}

class CaloriePreferencesRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("ollama_api_key")
        val OLLAMA_ADDRESS = stringPreferencesKey("ollama_address")
        val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val MEALS = stringPreferencesKey("todays_meals")
        val TODAYS_DATE = stringPreferencesKey("todays_date")
    }

    val preferenceFlow: Flow<CaloriePreferences> = context.calorieDataStore.data
        .map { preferences ->
            mapToCaloriePreferences(preferences)
        }

    private fun mapToCaloriePreferences(preferences: Preferences): CaloriePreferences {
        val today = LocalDate.now()
        val storedDate = preferences[Keys.TODAYS_DATE].toLocalDateOrNull()
        val history = parseMealHistory(preferences[Keys.MEALS], storedDate ?: today)
        val todaysMeals = history[today].orEmpty()
        return CaloriePreferences(
            apiKey = preferences[Keys.API_KEY].orEmpty(),
            ollamaAddress = preferences[Keys.OLLAMA_ADDRESS]?.takeIf { it.isNotBlank() }
                ?: CaloriePreferences.DEFAULT_OLLAMA_ADDRESS,
            ollamaModel = preferences[Keys.OLLAMA_MODEL]?.takeIf { it.isNotBlank() }
                ?: CaloriePreferences.DEFAULT_OLLAMA_MODEL,
            dailyGoal = preferences[Keys.DAILY_GOAL] ?: 2000,
            meals = todaysMeals,
            todaysTotal = todaysMeals.sumOf { it.calories },
            todaysDate = today,
            mealHistory = history
        )
    }

    suspend fun updateApiKey(apiKey: String) {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateOllamaAddress(address: String) {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.OLLAMA_ADDRESS] = address.trim()
        }
    }

    suspend fun updateOllamaModel(model: String) {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.OLLAMA_MODEL] = model.trim()
        }
    }

    suspend fun updateDailyGoal(goal: Int) {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL] = goal.coerceAtLeast(500)
        }
    }

    suspend fun addMeal(calories: Int, note: String) {
        if (calories == 0) return
        context.calorieDataStore.edit { prefs ->
            val today = LocalDate.now()
            val storedDate = prefs[Keys.TODAYS_DATE].toLocalDateOrNull()
            val history = parseMealHistory(prefs[Keys.MEALS], storedDate ?: today).toMutableMap()
            val meal = Meal(
                id = java.util.UUID.randomUUID().toString(),
                calories = calories,
                note = note.ifBlank { "Logged meal" },
                timestamp = System.currentTimeMillis()
            )
            history[today] = history[today].orEmpty() + meal
            prefs.writeMealHistory(history)
            prefs[Keys.TODAYS_DATE] = today.toString()
        }
    }

    suspend fun deleteMeal(id: String) {
        context.calorieDataStore.edit { prefs ->
            val today = LocalDate.now()
            val storedDate = prefs[Keys.TODAYS_DATE].toLocalDateOrNull()
            val history = parseMealHistory(prefs[Keys.MEALS], storedDate ?: today).toMutableMap()
            val entry = history.entries.firstOrNull { entry -> entry.value.any { it.id == id } }
            if (entry != null) {
                val updatedMeals = entry.value.filterNot { it.id == id }
                if (updatedMeals.isEmpty()) {
                    history.remove(entry.key)
                } else {
                    history[entry.key] = updatedMeals
                }
                prefs.writeMealHistory(history)
            }
            prefs[Keys.TODAYS_DATE] = today.toString()
        }
    }

    suspend fun resetToday() {
        context.calorieDataStore.edit { prefs ->
            val today = LocalDate.now()
            val storedDate = prefs[Keys.TODAYS_DATE].toLocalDateOrNull()
            val history = parseMealHistory(prefs[Keys.MEALS], storedDate ?: today).toMutableMap()
            history.remove(today)
            prefs.writeMealHistory(history)
            prefs[Keys.TODAYS_DATE] = today.toString()
        }
    }

    suspend fun exportDatabase(): String {
        val preferences = context.calorieDataStore.data.first()
        val state = mapToCaloriePreferences(preferences)
        val historyJson = state.mealHistory.toHistoryJson()
        return JSONObject().apply {
            put("apiKey", state.apiKey)
            put("ollamaAddress", state.ollamaAddress)
            put("ollamaModel", state.ollamaModel)
            put("dailyGoal", state.dailyGoal)
            put("mealHistory", historyJson?.let { JSONObject(it) } ?: JSONObject())
        }.toString()
    }

    suspend fun importDatabase(payload: String) {
        if (payload.isBlank()) throw IllegalArgumentException("Import data is empty")
        val json = JSONObject(payload)
        val now = LocalDate.now()
        val historyRaw = json.opt("mealHistory")?.let {
            when (it) {
                is JSONObject -> it.toString()
                is JSONArray -> it.toString()
                is String -> it
                else -> null
            }
        }
        val history = parseMealHistory(historyRaw, now)
        val apiKey = json.optString("apiKey", "")
        val address = json.optString("ollamaAddress", CaloriePreferences.DEFAULT_OLLAMA_ADDRESS)
        val model = json.optString("ollamaModel", CaloriePreferences.DEFAULT_OLLAMA_MODEL)
        val goal = json.optInt("dailyGoal", 2000).coerceAtLeast(500)

        context.calorieDataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.OLLAMA_ADDRESS] = address.trim().ifBlank { CaloriePreferences.DEFAULT_OLLAMA_ADDRESS }
            prefs[Keys.OLLAMA_MODEL] = model.trim().ifBlank { CaloriePreferences.DEFAULT_OLLAMA_MODEL }
            prefs[Keys.DAILY_GOAL] = goal
            prefs.writeMealHistory(history)
            prefs[Keys.TODAYS_DATE] = now.toString()
        }
    }

    private fun MutablePreferences.writeMealHistory(history: Map<LocalDate, List<Meal>>) {
        val serialized = history
            .filterValues { it.isNotEmpty() }
            .toHistoryJson()
        if (serialized.isNullOrBlank()) {
            remove(Keys.MEALS)
        } else {
            this[Keys.MEALS] = serialized
        }
    }
}

private fun parseMealHistory(raw: String?, fallbackDate: LocalDate): Map<LocalDate, List<Meal>> {
    if (raw.isNullOrBlank()) return emptyMap()
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("{") -> parseHistoryObject(trimmed)
        trimmed.startsWith("[") -> {
            val meals = Meal.listFromJson(trimmed)
            if (meals.isEmpty()) emptyMap() else mapOf(fallbackDate to meals)
        }
        else -> emptyMap()
    }
}

private fun parseHistoryObject(raw: String): Map<LocalDate, List<Meal>> {
    val jsonObject = runCatching { JSONObject(raw) }.getOrElse { return emptyMap() }
    val result = mutableMapOf<LocalDate, List<Meal>>()
    val keys = jsonObject.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
        val array = jsonObject.optJSONArray(key) ?: continue
        val meals = mutableListOf<Meal>()
        for (i in 0 until array.length()) {
            val mealObject = array.optJSONObject(i) ?: continue
            meals.add(Meal.fromJson(mealObject))
        }
        result[date] = meals
    }
    return result
}

private fun Map<LocalDate, List<Meal>>.toHistoryJson(): String? {
    if (isEmpty()) return null
    val jsonObject = JSONObject()
    entries
        .sortedByDescending { it.key }
        .forEach { (date, meals) ->
            if (meals.isEmpty()) return@forEach
            val array = JSONArray()
            meals.forEach { array.put(it.toJson()) }
            jsonObject.put(date.toString(), array)
        }
    return if (jsonObject.length() == 0) null else jsonObject.toString()
}

private fun String?.toLocalDateOrNull(): LocalDate? = this?.let {
    runCatching { LocalDate.parse(it) }.getOrNull()
}
