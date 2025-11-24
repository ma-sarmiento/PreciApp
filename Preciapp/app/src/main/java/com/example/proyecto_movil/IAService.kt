package com.example.proyecto_movil

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object IAService {

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun generarRespuesta(prompt: String, callback: (String?) -> Unit) {

        val apiKey = BuildConfig.OPENAI_API_KEY

        if (apiKey.isBlank()) {
            Log.e("IA_SERVICE", "❌ OPENAI_API_KEY está vacío")
            callback(null)
            return
        }

        val url = "https://api.openai.com/v1/chat/completions"

        // 🔥🔥🔥 IMPORTANTE: ESCAPAR EL TEXTO
        val safePrompt = JSONObject.quote(prompt)

        val bodyJson = """
        {
          "model": "gpt-4o-mini",
          "messages": [
            { "role": "user", "content": $safePrompt }
          ],
          "max_tokens": 300
        }
        """.trimIndent()

        Log.d("IA_SERVICE", "📤 JSON → $bodyJson")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("IA_SERVICE", "❌ Error: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d("IA_SERVICE", "📨 Respuesta → $body")

                if (!response.isSuccessful || body == null) {
                    callback(null)
                    return
                }

                try {
                    val json = JSONObject(body)
                    val texto = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    callback(texto)

                } catch (e: Exception) {
                    Log.e("IA_SERVICE", "❌ Error parseando JSON: ${e.message}")
                    callback(null)
                }
            }
        })
    }
}
