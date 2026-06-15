package com.example.caloriestracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.calorieDataStore by preferencesDataStore(name = "calorie_prefs")

data class CaloriePreferences(
    val apiKey: String = "",
    val ollamaAddress: String = DEFAULT_OLLAMA_ADDRESS,
    val ollamaModel: String = DEFAULT_OLLAMA_MODEL,
    val dailyGoal: Int = 2000,
    val meals: List<Meal> = emptyList(),
    val todaysTotal: Int = 0,
    val todaysDate: LocalDate = LocalDate.now()
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
        val storedDate = preferences[Keys.TODAYS_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val meals = if (storedDate == today) Meal.listFromJson(preferences[Keys.MEALS]) else emptyList()
        return CaloriePreferences(
            apiKey = preferences[Keys.API_KEY].orEmpty(),
            ollamaAddress = preferences[Keys.OLLAMA_ADDRESS]?.takeIf { it.isNotBlank() }
                ?: CaloriePreferences.DEFAULT_OLLAMA_ADDRESS,
            ollamaModel = preferences[Keys.OLLAMA_MODEL]?.takeIf { it.isNotBlank() }
                ?: CaloriePreferences.DEFAULT_OLLAMA_MODEL,
            dailyGoal = preferences[Keys.DAILY_GOAL] ?: 2000,
            meals = meals,
            todaysTotal = meals.sumOf { it.calories },
            todaysDate = today
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
        if (calories <= 0) return
        context.calorieDataStore.edit { prefs ->
            val today = LocalDate.now()
            val storedDate = prefs[Keys.TODAYS_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val current = if (storedDate == today) Meal.listFromJson(prefs[Keys.MEALS]) else emptyList()
            val meal = Meal(
                id = java.util.UUID.randomUUID().toString(),
                calories = calories,
                note = note.ifBlank { "Logged meal" },
                timestamp = System.currentTimeMillis()
            )
            prefs[Keys.MEALS] = Meal.listToJson(current + meal)
            prefs[Keys.TODAYS_DATE] = today.toString()
        }
    }

    suspend fun deleteMeal(id: String) {
        context.calorieDataStore.edit { prefs ->
            val today = LocalDate.now()
            val storedDate = prefs[Keys.TODAYS_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val current = if (storedDate == today) Meal.listFromJson(prefs[Keys.MEALS]) else emptyList()
            prefs[Keys.MEALS] = Meal.listToJson(current.filterNot { it.id == id })
            prefs[Keys.TODAYS_DATE] = today.toString()
        }
    }

    suspend fun resetToday() {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.MEALS] = Meal.listToJson(emptyList())
            prefs[Keys.TODAYS_DATE] = LocalDate.now().toString()
        }
    }
}
