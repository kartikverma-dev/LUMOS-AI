package com.lumos.ai.network

import com.lumos.ai.data.LumosSettings
import com.lumos.ai.data.db.MessageEntity
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to llama.cpp's llama-server (OpenAI-compatible /v1/chat/completions)
 * and consumes the SSE token stream.
 */
class LlamaApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // streaming: no idle timeout
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Separate short-timeout client for /health polling so a hung connection
    // can't stall the online/offline status indefinitely.
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isHealthy(baseUrl: String): Boolean {
        return try {
            val req = Request.Builder().url(baseUrl.trimEnd('/') + "/health").build()
            healthClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    fun buildChatBody(
        settings: LumosSettings,
        history: List<MessageEntity>
    ): JSONObject {
        val messages = JSONArray()
        if (settings.systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", settings.systemPrompt))
        }
        // Send last ~12 messages as context to stay within the context window.
        history.takeLast(12).forEach { m ->
            if (m.content.isNotBlank()) {
                messages.put(JSONObject().put("role", m.role).put("content", m.content))
            }
        }
        return JSONObject()
            .put("model", settings.modelName)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", settings.temperature.toDouble())
    }

    /**
     * Starts a streamed chat completion. Returns the [Call] so the caller can cancel it.
     */
    fun streamChat(
        settings: LumosSettings,
        history: List<MessageEntity>,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ): Call {
        val body = buildChatBody(settings, history)
            .toString()
            .toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url(settings.serverUrl.trimEnd('/') + "/v1/chat/completions")
            .post(body)
            .header("Accept", "text/event-stream")
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                onError("Connection failed: " + e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("Server returned HTTP " + response.code)
                    response.close()
                    return
                }
                try {
                    val reader = response.body!!.byteStream().bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (!l.startsWith("data:")) continue
                        val payload = l.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        try {
                            // NOTE: optString(name) with no fallback returns the literal
                            // string "null" when the key is absent (org.json quirk), not
                            // Kotlin null. The first SSE chunk is role-only with no
                            // "content" key, so always pass a "" fallback here.
                            val delta = JSONObject(payload)
                                .optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content", "")
                            if (!delta.isNullOrEmpty()) onToken(delta)
                        } catch (je: Exception) {
                            // skip malformed keep-alive chunks
                        }
                    }
                    onDone()
                } catch (e: Exception) {
                    if (!call.isCanceled()) onError("Stream error: " + e.message)
                } finally {
                    response.close()
                }
            }
        })
        return call
    }
}
