package com.example.caloriestracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caloriestracker.data.CaloriePreferences
import com.example.caloriestracker.data.CaloriePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CalorieTrackerUiState(
    val apiKey: String = "",
    val dailyGoal: Int = 2000,
    val todaysTotal: Int = 0,
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

    fun addMealCalories(calories: Int) {
        if (calories <= 0) return
        viewModelScope.launch {
            repository.addCalories(calories)
        }
    }

    fun updateApiKey(value: String) {
        viewModelScope.launch {
            repository.updateApiKey(value)
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
            dailyGoal = dailyGoal,
            todaysTotal = todaysTotal,
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
