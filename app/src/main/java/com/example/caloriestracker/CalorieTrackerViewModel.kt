package com.example.caloriestracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caloriestracker.data.CaloriePreferences
import com.example.caloriestracker.data.CaloriePreferencesRepository
import com.example.caloriestracker.data.Meal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalorieTrackerUiState(
    val apiKey: String = "",
    val ollamaAddress: String = CaloriePreferences.DEFAULT_OLLAMA_ADDRESS,
    val ollamaModel: String = CaloriePreferences.DEFAULT_OLLAMA_MODEL,
    val dailyGoal: Int = 2000,
    val meals: List<Meal> = emptyList(),
    val todaysTotal: Int = 0,
    val mealHistory: Map<LocalDate, List<Meal>> = emptyMap(),
    val isLoading: Boolean = true
)

class CalorieTrackerViewModel(
    private val repository: CaloriePreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalorieTrackerUiState())
    val uiState: StateFlow<CalorieTrackerUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.preferenceFlow.collect { prefs ->
                _uiState.value = prefs.toUiState()
            }
        }
    }

    fun addMeal(calories: Int, note: String) {
        if (calories <= 0) return
        viewModelScope.launch {
            repository.addMeal(calories, note)
        }
    }

    fun deleteMeal(id: String) {
        viewModelScope.launch {
            repository.deleteMeal(id)
        }
    }

    fun updateApiKey(value: String) {
        viewModelScope.launch {
            repository.updateApiKey(value)
        }
    }

    fun updateOllamaAddress(value: String) {
        viewModelScope.launch {
            repository.updateOllamaAddress(value)
        }
    }

    fun updateOllamaModel(value: String) {
        viewModelScope.launch {
            repository.updateOllamaModel(value)
        }
    }

    fun updateDailyGoal(goal: Int) {
        viewModelScope.launch {
            repository.updateDailyGoal(goal)
        }
    }

    private fun CaloriePreferences.toUiState(): CalorieTrackerUiState =
        CalorieTrackerUiState(
            apiKey = apiKey,
            ollamaAddress = ollamaAddress,
            ollamaModel = ollamaModel,
            dailyGoal = dailyGoal,
            meals = meals,
            todaysTotal = todaysTotal,
            mealHistory = mealHistory,
            isLoading = false
        )

    companion object {
        fun factory(repository: CaloriePreferencesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CalorieTrackerViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return CalorieTrackerViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
