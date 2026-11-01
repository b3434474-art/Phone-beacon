package com.phonebeacon.controller

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.MessageDigest

internal class BeaconApi(context: Context) {
    private val prefs = context.getSharedPreferences("phonebeacon_private", Context.MODE_PRIVATE)
    private fun secret(): String {
        prefs.getString("controller_secret", null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            .also { prefs.edit().putString("controller_secret", it).apply() }
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

    fun createCode(): String = call(
        "phonebeacon_create_pair_code",
        JSONObject().put("p_controller_secret", secret())
    ).trim().trim('"')

    fun send(command: String): Boolean = call(
        "phonebeacon_send_command",
        JSONObject().put("p_controller_secret", secret()).put("p_command", command)
    ).trim() == "true"
}
