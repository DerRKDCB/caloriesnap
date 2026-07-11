# AGENTS

## Stack & Entry Points
- Single-module Android app under `app/` (Gradle 8.4 / Kotlin 1.9.24, Compose Material 3). `MainActivity` wires the only `NavHost` with `home`, `camera`, `settings` routes backed by a single `CalorieTrackerViewModel` (`app/src/main/java/com/example/caloriestracker/MainActivity.kt`).
- Treat `CalorieTrackerViewModel` + `CaloriePreferencesRepository` as the only state boundary (`app/src/main/java/com/example/caloriestracker/CalorieTrackerViewModel.kt`, `.../data/CaloriePreferencesRepository.kt`). All UI mutations call the viewmodel so DataStore updates stay serialized inside `viewModelScope`.

## Build, Lint, Test
- Use the wrapper: `./gradlew :app:assembleDebug` (APK) and `./gradlew :app:installDebug` (deploy). Studio sync relies on the same tasks.
- Lint/types: `./gradlew :app:lintDebug` and `./gradlew :app:testDebugUnitTest`. There are no unit tests yet, but this guards regressions such as missing resources.
- Instrumented tests (none checked in) still run Compose previews; if you add any, use `./gradlew :app:connectedDebugAndroidTest` with an emulator/real device that has CameraX support.
- Core library desugaring is enabled; keep Java 17 bytecode targets aligned with Gradle (`android.compileOptions` + `kotlinOptions.jvmTarget=17`).

## State & Persistence
- Persistence lives in a single Preferences DataStore named `calorie_prefs`. The repository stores `mealHistory` as a JSON object keyed by ISO `LocalDate`, plus a `todays_date` string that drives the daily reset (`app/src/main/java/com/example/caloriestracker/data/CaloriePreferencesRepository.kt`). Bypass this logic and you will resurrect prior-day meals.
- `exportDatabase()` / `importDatabase()` move that same JSON payload through Settings. If you change the schema, update both serialization helpers (`toHistoryJson`, `parseMealHistory`) and the Settings file import/export UX together.
- `dailyGoal` is coerced to ≥500 kcal and every meal add/delete updates `todays_date`. Uphold those guards when adding new repository entry points.

## AI Estimator & Networking
- `CalorieEstimator` is the only place that talks to Ollama (`app/src/main/java/com/example/caloriestracker/ai/CalorieEstimator.kt`). Keep all new AI flows going through it so the deterministic fallback (seeded by bitmap/description) remains available for offline demos and tests.
- Remote calls always hit `<address>/api/generate` with a JSON-only response contract. The parser extracts the first `{...}` block or falls back to regexes; if you change prompts, ensure the model still returns parseable JSON for calories/confidence/note.
- `postJson` manually handles redirects to preserve the `Authorization` header. Reuse it (or honor the same behavior) or the `Test connection` button in Settings will break against HTTPS proxies.

## UI & Feature Notes
- `HomeScreen` drives everything: it keeps a local date picker state, pulls historic meals from `uiState.mealHistory`, and pipes manual + AI text entries through `onManualEntry`. Any new logging flow must ultimately call `CalorieTrackerViewModel.addMeal` / `addDescriptionMeal` to ensure DataStore + history stay in sync (`app/src/main/java/com/example/caloriestracker/ui/HomeScreen.kt`).
- `CameraScreen` uses CameraX `ImageCapture` bound inside a `DisposableEffect` and rotates temp files with `ExifInterface` before feeding them to `CalorieEstimator`. Preserve the temp-file workflow and `hasCameraPermission` gate so previews don’t crash on devices without a camera (`app/src/main/java/com/example/caloriestracker/ui/CameraScreen.kt`).
- Settings relies on suspend lambdas for import/export and `CalorieEstimator.testConnection`. It stages export payloads before launching the system document picker; if you change signatures, keep the coroutine + ActivityResult flow compatible (`app/src/main/java/com/example/caloriestracker/ui/SettingsScreen.kt`).

## Ollama & Configuration
- Defaults come from `CaloriePreferences.DEFAULT_OLLAMA_ADDRESS` (`https://ollama.com/api`) and model `gemma4:31b-cloud`. The Settings “Test connection” button surfaces raw HTTP errors, so bubble up server messages when touching that code.
- API keys and server details are stored verbatim in DataStore. There is no env var loading—any new secrets need an in-app settings surface or a mock fallback.

## Practical Tips
- Need to inspect or reset data? Use the Settings export/import buttons; deleting the DataStore file is harder on-device.
- Camera flows require the `CAMERA` runtime permission even on emulators. If you script UI tests, mock `CalorieEstimator` instead of trying to virtualize the camera hardware.
- Keep `Meal` serialization backward compatible (`app/src/main/java/com/example/caloriestracker/data/Meal.kt`); existing exports expect the `id/calories/note/timestamp` schema.
