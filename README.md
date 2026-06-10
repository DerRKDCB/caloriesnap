# Calorie Snap

Lightweight calorie tracking app for Android that keeps a running total for the current day, lets you snap meals with one tap, and pipes the photo through an Ollama-powered vision model (via your API key) to estimate calories.

## Highlights

- **Home dashboard** – Shows today’s intake, goal progress, and remaining calories at a glance.
- **One-tap camera** – Launch the CameraX capture flow, snap a meal, and add the AI estimate directly to the day’s total.
- **Configurable AI** – Settings screen stores your Ollama API key and preferred daily calorie goal via DataStore.
- **Extensible estimator** – `CalorieEstimator` handles the Ollama HTTP request; if the key is missing (or the call fails) it falls back to a deterministic on-device stub for demos.
- **Modern stack** – Compose Material 3, Navigation Compose, DataStore, CameraX, and coroutines, wrapped in a Gradle 8.7 project ready for Android Studio Iguana+.

## Project structure

```
app/
 ├─ src/main/java/com/example/caloriestracker/
 │   ├─ MainActivity.kt                 # Navigation hub for Home, Camera, Settings
 │   ├─ CalorieTrackerViewModel.kt      # Shared state + DataStore bridge
 │   ├─ data/CaloriePreferencesRepository.kt
 │   ├─ ui/{Home,Camera,Settings}Screen.kt
 │   └─ ai/CalorieEstimator.kt          # Ollama client with stub fallback
 ├─ src/main/res/                       # Themes, strings, icons, XML resources
 └─ build.gradle.kts                    # Compose, CameraX, Navigation, DataStore deps
```

## Getting started

1. **Open in Android Studio** (Giraffe or newer) and sync the Gradle 8.7 project.
2. **Run on device/emulator** (API 24+). The home screen shows today’s total; tap *Snap meal* to open the camera.
3. **Enter your Ollama API key + goal** via the settings icon in the top-right before capturing if you want live AI estimates.
4. **Customize AI** by editing `CalorieEstimator` (e.g., change the endpoint/model). Return a `CalorieEstimate` and the rest of the UI updates automatically.

### Command line build

```bash
./gradlew assembleDebug
./gradlew connectedAndroidTest   # requires running emulator/device
```

### Notes

- Ollama endpoint defaults to `http://localhost:11434/api/generate`. Point it to your server or proxy if you’re not running locally.
- When the API key is missing (or the request fails) the estimator falls back to a deterministic pseudo-random value so the flow remains testable.
- DataStore keeps today’s total, the API key, and the calorie goal. Totals reset automatically when the calendar date changes.
