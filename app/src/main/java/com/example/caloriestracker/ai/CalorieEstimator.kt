package com.example.caloriestracker.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class CalorieEstimate(
    val calories: Int,
    val confidence: Float,
    val note: String
)

class EstimateException(
    detail: String,
    cause: Throwable? = null
) : Exception(detail, cause)

/**
 * Result of probing the configured Ollama instance from Settings.
 */
sealed interface OllamaTestResult {
    data class Success(val message: String) : OllamaTestResult
    data class Failure(val message: String) : OllamaTestResult
}

/**
 * Talks to a (configurable) Ollama instance to interpret meal photos and
 * estimate workout calories.  Throws [EstimateException] when no server is
 * configured or when the remote response cannot be parsed — the exception
 * message includes the raw response so callers can log or display it.
 */
object CalorieEstimator {
    private const val DEFAULT_ADDRESS = "https://ollama.com/api"
    private const val USER_AGENT = "CalorieSnap/1.0 (Android)"
    private enum class PromptContext { MEAL, WORKOUT }

    suspend fun estimate(
        bitmap: Bitmap,
        apiKey: String?,
        address: String,
        model: String
    ): CalorieEstimate {
        if (address.isNotBlank() && model.isNotBlank()) {
            return remoteEstimate(bitmap, apiKey, address, model)
        }
        throw EstimateException(
            "AI server not configured (address=${address.orEmpty().take(30)}, model=$model). " +
                "Set an Ollama address and model in Settings before estimating from a photo."
        )
    }

    suspend fun estimateFromDescription(
        description: String,
        apiKey: String?,
        address: String,
        model: String
    ): CalorieEstimate {
        val input = description.trim()
        require(input.isNotBlank()) { "Description cannot be empty" }
        if (address.isNotBlank() && model.isNotBlank()) {
            return remoteEstimate(input, apiKey, address, model, PromptContext.MEAL)
        }
        throw EstimateException(
            "AI server not configured (address=${address.orEmpty().take(30)}, model=$model). " +
                "Set an Ollama address and model in Settings before estimating from a description."
        )
    }

    suspend fun estimateWorkoutFromDescription(
        description: String,
        apiKey: String?,
        address: String,
        model: String
    ): CalorieEstimate {
        val input = description.trim()
        require(input.isNotBlank()) { "Description cannot be empty" }
        if (address.isNotBlank() && model.isNotBlank()) {
            return remoteEstimate(input, apiKey, address, model, PromptContext.WORKOUT)
        }
        throw EstimateException(
            "AI server not configured (address=${address.orEmpty().take(30)}, model=$model). " +
                "Set an Ollama address and model in Settings before estimating workout calories."
        )
    }

    /**
     * Sends a tiny real request to the configured Ollama instance to verify the
     * address, credentials and that the chosen model actually responds. Used by
     * the "Test" button in Settings. On failure it surfaces the HTTP status and
     * the server's error body so problems can be diagnosed.
     */
    suspend fun testConnection(
        address: String,
        model: String,
        apiKey: String?
    ): OllamaTestResult = withContext(Dispatchers.IO) {
        val base = normalizeBase(address)
        if (base.isBlank()) {
            return@withContext OllamaTestResult.Failure("Enter an Ollama address first.")
        }
        if (model.isBlank()) {
            return@withContext OllamaTestResult.Failure("Enter a model name first.")
        }
        val endpoint = "$base/api/generate"
        val payload = JSONObject().apply {
            put("model", model)
            put("prompt", "ping")
            put("stream", false)
        }
        runCatching { postJson(endpoint, payload.toString(), apiKey) }.fold(
            onSuccess = { (code, body) ->
                when (code) {
                    in 200..299 ->
                        OllamaTestResult.Success("Connected. Model \"$model\" responded successfully.")
                    401, 403 ->
                        OllamaTestResult.Failure("HTTP $code: authentication failed. Check your API key. ${serverError(body)}".trim())
                    404 ->
                        OllamaTestResult.Failure("HTTP 404: model \"$model\" not found at $endpoint. ${serverError(body)}".trim())
                    else ->
                        OllamaTestResult.Failure("HTTP $code from $endpoint. ${serverError(body)}".trim())
                }
            },
            onFailure = { error ->
                OllamaTestResult.Failure(
                    "Could not reach $endpoint: ${error.localizedMessage ?: error.javaClass.simpleName}"
                )
            }
        )
    }

    /** Pulls the "error" field out of an Ollama JSON error body when present. */
    private fun serverError(body: String): String {
        if (body.isBlank()) return ""
        val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
        val text = if (!message.isNullOrBlank()) message else body
        return text.take(200)
    }

    /**
     * POSTs a JSON body, manually following redirects so the Authorization
     * header (and the body) survive an http -> https hop. Android's default
     * HttpURLConnection drops the auth header across redirects, which surfaces
     * as a spurious 401/403. Returns the final status code and response text.
     */
    private fun postJson(
        endpoint: String,
        jsonBody: String,
        apiKey: String?,
        redirectsLeft: Int = 3
    ): Pair<Int, String> {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
            doInput = true
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            connection.outputStream.use { stream ->
                OutputStreamWriter(stream).use { it.write(jsonBody) }
            }
            val code = connection.responseCode
            if (code in 300..399 && redirectsLeft > 0) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    val nextUrl = URL(URL(endpoint), location).toString()
                    connection.disconnect()
                    return postJson(nextUrl, jsonBody, apiKey, redirectsLeft - 1)
                }
            }
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    /** Strips trailing slashes and any known endpoint suffix to get the base URL. */
    private fun normalizeBase(address: String): String {
        var base = address.trim().trimEnd('/')
        base = base.removeSuffix("/api/generate").trimEnd('/')
        base = base.removeSuffix("/generate").trimEnd('/')
        base = base.removeSuffix("/api").trimEnd('/')
        return base
    }

    private suspend fun remoteEstimate(
        bitmap: Bitmap,
        apiKey: String?,
        address: String,
        model: String
    ): CalorieEstimate {
        val response = withContext(Dispatchers.IO) {
            val base = normalizeBase(address)
            val endpoint = "$base/api/generate"
            val payload = JSONObject().apply {
                put("model", model)
                put(
                    "prompt",
                    "You are a nutrition assistant. Look at the food in this image and estimate its total calories. " +
                        "Respond with ONLY a compact JSON object and nothing else, no markdown, no explanation: " +
                        "{\"calories\": <integer kcal>, \"confidence\": <number between 0 and 1>, \"note\": \"<short description of the food>\"}."
                )
                put("stream", false)
                put("images", org.json.JSONArray().put(bitmap.toBase64()))
            }
            val (code, body) = postJson(endpoint, payload.toString(), apiKey)
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code: ${serverError(body)}")
            }
            body
        }
        return parseRemoteEstimate(response) ?: throw EstimateException(
            "Could not parse AI response for image estimate. " +
                "Raw response: ${response.take(500)}",
            cause = java.io.IOException("parseRemoteEstimate returned null")
        )
    }

    private suspend fun remoteEstimate(
        description: String,
        apiKey: String?,
        address: String,
        model: String,
        context: PromptContext
    ): CalorieEstimate {
        val response = withContext(Dispatchers.IO) {
            val base = normalizeBase(address)
            val endpoint = "$base/api/generate"
            val prompt = when (context) {
                PromptContext.MEAL ->
                    "You are a nutrition assistant. Estimate the total calories for the described meal: \"$description\". " +
                        "Respond ONLY with a compact JSON object: {\"calories\": <integer>, \"confidence\": <0-1>, \"note\": \"short description\"}."
                PromptContext.WORKOUT ->
                    "You are a fitness coach. Estimate the calories burned for this workout: \"$description\". " +
                        "Respond ONLY with JSON: {\"calories\": <integer calories burned>, \"confidence\": <0-1>, \"note\": \"short workout summary\"}."
            }
            val payload = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
            }
            val (code, body) = postJson(endpoint, payload.toString(), apiKey)
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code: ${serverError(body)}")
            }
            body
        }
        return parseRemoteEstimate(response) ?: throw EstimateException(
            "Could not parse AI response for ${context.name.lowercase()} estimate. " +
                "Raw response: ${response.take(500)}",
            cause = java.io.IOException("parseRemoteEstimate returned null")
        )
    }

    private fun parseRemoteEstimate(body: String): CalorieEstimate? {
        return runCatching {
            // Ollama's /api/generate wraps the model output in {"response": "..."}.
            val outer = runCatching { JSONObject(body) }.getOrNull()
            val responseText = outer?.optString("response").orEmpty().ifBlank { body }

            // The model is asked to reply with a JSON object; it may still wrap it
            // in prose or a ```json fence, so pull out the first {...} block.
            val inner = extractJsonObject(responseText)

            val source = inner ?: outer
            val calories = when {
                source?.has("calories") == true -> source.optDouble("calories")
                else -> extractCalories(responseText)?.toDouble()
            }
            val confidence = when {
                source?.has("confidence") == true -> source.optDouble("confidence")
                else -> extractConfidence(responseText)
            }
            val description = when {
                source?.has("note") == true -> source.optString("note")
                inner == null && responseText.isNotBlank() -> responseText.trim().take(120)
                else -> "Estimated from photo"
            }

            if (calories != null && !calories.isNaN()) {
                CalorieEstimate(
                    calories = calories.roundToInt().coerceAtLeast(0),
                    confidence = confidence?.takeIf { !it.isNaN() }?.coerceIn(0.1, 1.0)?.toFloat() ?: 0.8f,
                    note = description.ifBlank { "Estimated from photo" }
                )
            } else null
        }.getOrNull()
    }

    /** Finds and parses the first balanced {...} JSON object inside a string. */
    private fun extractJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val candidate = text.substring(start, i + 1)
                        return runCatching { JSONObject(candidate) }.getOrNull()
                    }
                }
            }
        }
        return null
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
