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
    val dailyGoal: Int = 2000,
    val todaysTotal: Int = 0,
    val todaysDate: LocalDate = LocalDate.now()
)

class CaloriePreferencesRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("ollama_api_key")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val TODAYS_TOTAL = intPreferencesKey("todays_total")
        val TODAYS_DATE = stringPreferencesKey("todays_date")
    }

    val preferenceFlow: Flow<CaloriePreferences> = context.calorieDataStore.data
        .map { preferences ->
            mapToCaloriePreferences(preferences)
        }

    private fun mapToCaloriePreferences(preferences: Preferences): CaloriePreferences {
        val today = LocalDate.now()
        val storedDate = preferences[Keys.TODAYS_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val total = if (storedDate == today) preferences[Keys.TODAYS_TOTAL] ?: 0 else 0
        return CaloriePreferences(
            apiKey = preferences[Keys.API_KEY].orEmpty(),
            dailyGoal = preferences[Keys.DAILY_GOAL] ?: 2000,
            todaysTotal = total,
            todaysDate = today
        )
    }

    suspend fun updateApiKey(apiKey: String) {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateDailyGoal(goal: Int) {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL] = goal.coerceAtLeast(500)
        }
    }

    suspend fun addCalories(amount: Int) {
        context.calorieDataStore.edit { prefs ->
            val today = LocalDate.now()
            val storedDate = prefs[Keys.TODAYS_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val base = if (storedDate == today) prefs[Keys.TODAYS_TOTAL] ?: 0 else 0
            prefs[Keys.TODAYS_TOTAL] = base + amount
            prefs[Keys.TODAYS_DATE] = today.toString()
        }
    }

    suspend fun resetToday() {
        context.calorieDataStore.edit { prefs ->
            prefs[Keys.TODAYS_TOTAL] = 0
            prefs[Keys.TODAYS_DATE] = LocalDate.now().toString()
        }
    }
}
