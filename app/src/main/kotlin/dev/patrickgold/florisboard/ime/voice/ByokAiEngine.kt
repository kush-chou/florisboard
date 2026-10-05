/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors / Foldboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ByokAiEngine(private val context: Context) {

    private val prefs = context.getSharedPreferences("foldboard_ai_prefs", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var provider: String
        get() = prefs.getString("byok_provider", "none") ?: "none"
        set(v) = prefs.edit().putString("byok_provider", v).apply()

    var apiKey: String
        get() = prefs.getString("byok_api_key", "") ?: ""
        set(v) = prefs.edit().putString("byok_api_key", v).apply()

    var endpoint: String
        get() = prefs.getString("byok_endpoint", "") ?: ""
        set(v) = prefs.edit().putString("byok_endpoint", v).apply()

    var model: String
        get() = prefs.getString("byok_model", "") ?: ""
        set(v) = prefs.edit().putString("byok_model", v).apply()

    fun processText(rawText: String, onComplete: (String) -> Unit) {
        val currentProvider = provider
        val currentKey = apiKey.trim()
        val currentEndpoint = endpoint.trim()

        if (currentProvider == "none" || (currentKey.isEmpty() && currentEndpoint.isEmpty())) {
            val localResult = LocalAiEngine.formatSpeech(rawText)
            mainHandler.post { onComplete(localResult) }
            return
        }

        executor.execute {
            var result = rawText
            try {
                result = when (currentProvider) {
                    "gemini" -> callGemini(rawText, currentKey, model.ifEmpty { "gemini-1.5-flash" })
                    "openai" -> callOpenAi(rawText, currentKey, currentEndpoint.ifEmpty { "https://api.openai.com/v1/chat/completions" }, model.ifEmpty { "gpt-4o-mini" })
                    "ollama" -> callOllama(rawText, currentEndpoint.ifEmpty { "http://localhost:11434/api/generate" }, model.ifEmpty { "llama3.2" })
                    else -> LocalAiEngine.formatSpeech(rawText)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                result = LocalAiEngine.formatSpeech(rawText)
            }

            mainHandler.post {
                onComplete(result)
            }
        }
    }

    private fun callGemini(text: String, key: String, modelName: String): String {
        val targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key"
        val url = URL(targetUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val prompt = "Format and clean this transcribed speech for clarity and punctuation. Keep the exact meaning, do not add explanations:\n\n$text"
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val parts = candidate.getJSONObject("content").getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text").trim()
                }
            }
        }
        return LocalAiEngine.formatSpeech(text)
    }

    private fun callOpenAi(text: String, key: String, endpointUrl: String, modelName: String): String {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val systemPrompt = "You are a speech transcription assistant. Clean up punctuation, casing, and typos. Output ONLY the polished text without quotes or explanations."
        val payload = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                return choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
            }
        }
        return LocalAiEngine.formatSpeech(text)
    }

    private fun callOllama(text: String, endpointUrl: String, modelName: String): String {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 8000

        val prompt = "Format and clean this speech transcription without commentary:\n\n$text"
        val payload = JSONObject().apply {
            put("model", modelName)
            put("prompt", prompt)
            put("stream", false)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            return json.optString("response", text).trim()
        }
        return LocalAiEngine.formatSpeech(text)
    }
}
