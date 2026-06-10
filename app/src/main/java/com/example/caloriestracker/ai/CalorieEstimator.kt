package com.example.caloriestracker.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random

data class CalorieEstimate(
    val calories: Int,
    val confidence: Float,
    val note: String
)

/**
 * Placeholder for the future AI-powered calorie interpretation service.
 * Currently returns a pseudo-random yet stable value derived from the bitmap
 * so that the UI can be verified without an actual backend.
 */
object CalorieEstimator {
    private const val OLLAMA_ENDPOINT = "http://localhost:11434/api/generate"
    private val descriptors = listOf(
        "Looks like a balanced plate",
        "Carb heavy portion",
        "Lean protein focused serving",
        "Dense and indulgent treat",
        "Fresh bowl of greens"
    )

    suspend fun estimate(bitmap: Bitmap, apiKey: String?): CalorieEstimate {
        if (!apiKey.isNullOrBlank()) {
            return runCatching { remoteEstimate(bitmap, apiKey) }
                .getOrElse { fallbackEstimate(bitmap) }
        }
        return fallbackEstimate(bitmap)
    }

    private suspend fun remoteEstimate(bitmap: Bitmap, apiKey: String): CalorieEstimate {
        val response = withContext(Dispatchers.IO) {
            val url = URL(OLLAMA_ENDPOINT)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doInput = true
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val payload = JSONObject().apply {
                put("prompt", "Estimate calories for this meal photo and respond with JSON {\"calories\": number, \"confidence\": number, \"note\": string}.")
                put("image", bitmap.toBase64())
            }

            connection.outputStream.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write(payload.toString())
                }
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        }
        return parseRemoteEstimate(response) ?: fallbackEstimate(bitmap)
    }

    private suspend fun fallbackEstimate(bitmap: Bitmap): CalorieEstimate {
        delay(1200)
        val seed = bitmap.byteCount + bitmap.width * bitmap.height
        val random = Random(seed.toLong())
        val calories = 180 + (seed % 620).toInt().absoluteValue
        val confidence = 0.65f + random.nextFloat() * 0.3f
        val note = descriptors[random.nextInt(descriptors.size)]
        return CalorieEstimate(calories, confidence.coerceAtMost(0.97f), note)
    }

    private fun parseRemoteEstimate(body: String): CalorieEstimate? {
        return runCatching {
            val json = JSONObject(body)
            val calories = when {
                json.has("calories") -> json.getDouble("calories")
                json.has("response") -> extractCalories(json.getString("response"))?.toDouble()
                else -> null
            }
            val confidence = when {
                json.has("confidence") -> json.getDouble("confidence")
                json.has("response") -> extractConfidence(json.getString("response"))
                else -> null
            }
            val description = when {
                json.has("note") -> json.getString("note")
                json.has("response") -> json.getString("response")
                else -> null
            }
            if (calories != null && description != null) {
                CalorieEstimate(
                    calories = calories.roundToInt().coerceAtLeast(0),
                    confidence = confidence?.coerceIn(0.1, 1.0)?.toFloat() ?: 0.8f,
                    note = description
                )
            } else null
        }.getOrNull()
    }

    private fun extractCalories(text: String): Int? {
        val regex = Regex("(\\d{2,4})\\s*(kcal|cal|calories)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractConfidence(text: String): Double? {
        val regex = Regex("(\\d{1,2})%", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.toDouble()?.div(100.0)
    }

    private fun Bitmap.toBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
