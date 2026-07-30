# Calorie Snap

Lightweight calorie tracking app for Android. Log meals by snapping a photo, typing a description, or entering calories manually. Estimates are powered by a local [Ollama](https://ollama.ai) vision/LLM model.

## Features

- **AI-powered estimates** – Snap a meal photo or describe what you ate. Calorie, confidence, and description come back from your Ollama instance.
- **Workout tracking** – Describe a workout and get an estimated burn. Calories are subtracted from your daily total.
- **Manual entry** – Log calories directly with an optional note and workout toggle.
- **Daily dashboard** – See today's intake, goal progress, and remaining calories at a glance. Navigate past days with the date picker to review history.
- **Persistent history** – All meals are stored in Preferences DataStore, keyed by date. Totals reset automatically when the calendar date changes.
- **Full import/export** – Export your meal history and settings as JSON, or restore from a previous export. Built into the Settings screen.
- **Configurable AI backend** – Point the app at any Ollama server, choose a model, and test the connection—all from Settings.
- **Expandable error details** – When an AI estimate fails, the app shows "Something went wrong" with a collapsible trace and a one-tap copy button for easy debugging.

## Project structure

```
app/
├── src/main/
│   ├── java/com/example/caloriestracker/
│   │   ├── MainActivity.kt                    # Navigation: Home, Camera, Settings
│   │   ├── CalorieTrackerViewModel.kt         # Shared UI state + DataStore bridge
│   │   ├── data/
│   │   │   ├── CaloriePreferencesRepository.kt  # DataStore persistence + export/import
│   │   │   └── Meal.kt                          # Meal data class + JSON serialization
│   │   ├── ui/
│   │   │   ├── HomeScreen.kt                  # Dashboard, date nav, entry dialogs
│   │   │   ├── CameraScreen.kt                # CameraX capture + AI estimate
│   │   │   ├── SettingsScreen.kt              # Backend config, goal, import/export
│   │   │   ├── ExpandableError.kt             # Collapsible error with copy-to-clipboard
│   │   │   └── theme/                         # Material 3 theme (Color, Type, Theme)
│   │   └── ai/
│   │       └── CalorieEstimator.kt            # Ollama client + AI estimation
│   └── res/                                   # Themes, strings, launcher icons, XML
└── build.gradle.kts
```

## Getting started

### Prerequisites

- Android Studio Iguana (2023.2.1) or newer
- JDK 17+
- An Android device / emulator running API 24+
- (Optional) An [Ollama](https://ollama.ai) server with a model such as `gemma4:31b-cloud`

### Setup

1. **Open** the project in Android Studio and let Gradle sync.
2. **Run** on a device or emulator (`./gradlew :app:installDebug`).
3. **(Optional) Configure AI** – Open Settings (gear icon), enter your Ollama server address and model name, then tap **Test connection**.
4. **Log a meal** – Tap **Snap meal** to use the camera, **Text** for an AI text estimate, **Manual** to enter calories directly, or **Workout** to log exercise.

### Command-line build

```bash
./gradlew :app:assembleDebug        # Build debug APK
./gradlew :app:installDebug         # Build + install on connected device
./gradlew :app:lintDebug            # Lint checks
```

## Configuration

| Setting | Default | Description |
|---|---|---|
| **Ollama address** | `https://ollama.com/api` | Base URL of your Ollama server |
| **Model** | `gemma4:31b-cloud` | Ollama model for image/text estimation |
| **API key** | *(empty)* | Optional Bearer token for authenticated endpoints |
| **Daily goal** | 2000 kcal | Daily calorie target (minimum 500) |

All settings are persisted in a Preferences DataStore. The estimator throws an [EstimateException](app/src/main/java/com/example/caloriestracker/ai/CalorieEstimator.kt) with the raw server response embedded in the message when a request fails or when no server is configured.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Compose Material 3 + Material Icons Extended |
| Navigation | Navigation Compose 2.7.7 |
| Camera | CameraX 1.3.3 (Preview + ImageCapture) |
| Persistence | Preferences DataStore |
| AI client | Raw `HttpURLConnection` + JSON |
| Image parsing | ExifInterface for rotation correction |
| Build | Gradle 8.7, AGP 8.4.1, Java 17 desugaring |

## Disclaimer

This project was vibe coded — most of it was written through natural language prompts to an AI coding assistant. It works, but treat it as a prototype. Review, test, and tweak before relying on it for anything serious.

## License

MIT License — see [LICENSE](/LICENSE) for details.

Copyright (c) 2026 DerRKDCB
