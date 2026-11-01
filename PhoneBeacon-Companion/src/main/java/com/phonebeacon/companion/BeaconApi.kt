package com.phonebeacon.companion

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

internal class BeaconApi(context: Context) {
    private val prefs = context.getSharedPreferences("phonebeacon_private", Context.MODE_PRIVATE)
    private fun secret(): String {
        prefs.getString("companion_secret", null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            .also { prefs.edit().putString("companion_secret", it).apply() }
    }

    private fun call(function: String, body: JSONObject): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        require(base.startsWith("https://") && key.isNotBlank()) {
            "Add PHONEBEACON_SUPABASE_URL and PHONEBEACON_SUPABASE_ANON_KEY to your local Gradle properties first."
        }
        val connection = (URL("$base/rest/v1/rpc/$function").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val result = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("Backend error: $result")
        return result
    }

    fun claim(code: String): Boolean = call(
        "phonebeacon_claim_pair_code",
        JSONObject().put("p_code", code).put("p_companion_secret", secret())
    ).trim() == "true"

    fun poll(): List<String> {
        val json = JSONArray(call(
            "phonebeacon_take_commands",
            JSONObject().put("p_companion_secret", secret())
        ))
        return (0 until json.length()).mapNotNull { json.optJSONObject(it)?.optString("command") }
    }

    fun isConfigured() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
}
