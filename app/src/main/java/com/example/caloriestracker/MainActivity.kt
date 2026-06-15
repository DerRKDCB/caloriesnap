package com.example.caloriestracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.caloriestracker.data.CaloriePreferencesRepository
import com.example.caloriestracker.ui.CameraScreen
import com.example.caloriestracker.ui.HomeScreen
import com.example.caloriestracker.ui.SettingsScreen
import com.example.caloriestracker.ui.theme.CaloriesTrackerTheme

class MainActivity : ComponentActivity() {

    private val trackerViewModel: CalorieTrackerViewModel by viewModels {
        CalorieTrackerViewModel.factory(CaloriePreferencesRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CaloriesTrackerTheme {
                val navController = rememberNavController()
                val uiState by trackerViewModel.uiState.collectAsStateWithLifecycle()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            state = uiState,
                            onOpenCamera = { navController.navigate("camera") },
                            onOpenSettings = { navController.navigate("settings") },
                            onDeleteMeal = { id -> trackerViewModel.deleteMeal(id) }
                        )
                    }
                    composable("camera") {
                        CameraScreen(
                            apiKey = uiState.apiKey,
                            ollamaAddress = uiState.ollamaAddress,
                            ollamaModel = uiState.ollamaModel,
                            onClose = { navController.popBackStack() },
                            onMealLogged = { calories, note -> trackerViewModel.addMeal(calories, note) }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            apiKey = uiState.apiKey,
                            ollamaAddress = uiState.ollamaAddress,
                            ollamaModel = uiState.ollamaModel,
                            dailyGoal = uiState.dailyGoal,
                            onBack = { navController.popBackStack() },
                            onApiKeyChange = trackerViewModel::updateApiKey,
                            onAddressChange = trackerViewModel::updateOllamaAddress,
                            onModelChange = trackerViewModel::updateOllamaModel,
                            onGoalChange = trackerViewModel::updateDailyGoal
                        )
                    }
                }
            }
        }
    }
}
