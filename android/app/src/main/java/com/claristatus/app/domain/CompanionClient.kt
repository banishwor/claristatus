package com.claristatus.app.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class CompanionStatus(
    val isOnline: Boolean,
    val isWhatsAppConnected: Boolean,
    val defaultPhone: String?
)

object CompanionClient {

    private const val PREFS_NAME = "claristatus_companion_prefs"
    private const val KEY_SERVER_IP = "companion_server_ip"
    private const val KEY_PHONE_NUMBER = "companion_phone_number"
    const val DEFAULT_IP = "192.168.1.5"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getServerIp(context: Context): String {
        return getPrefs(context).getString(KEY_SERVER_IP, DEFAULT_IP) ?: DEFAULT_IP
    }

    fun setServerIp(context: Context, ip: String) {
        getPrefs(context).edit().putString(KEY_SERVER_IP, ip.trim()).apply()
    }

    fun getPhoneNumber(context: Context): String {
        return getPrefs(context).getString(KEY_PHONE_NUMBER, "") ?: ""
    }

    fun setPhoneNumber(context: Context, phone: String) {
        val cleanPhone = phone.replace(Regex("\\D"), "")
        getPrefs(context).edit().putString(KEY_PHONE_NUMBER, cleanPhone).apply()
    }

    suspend fun checkCompanionStatus(context: Context, serverIp: String = getServerIp(context)): Result<CompanionStatus> =
        withContext(Dispatchers.IO) {
            try {
                val url = "http://${serverIp.trim()}:3000/status"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP error ${response.code}"))
                    }
                    val jsonStr = response.body?.string().orEmpty()
                    val json = JSONObject(jsonStr)
                    val isConnected = json.optBoolean("isWhatsAppConnected", false)
                    val defaultPhone = if (json.has("defaultPhone") && !json.isNull("defaultPhone")) json.getString("defaultPhone") else null
                    Result.success(CompanionStatus(isOnline = true, isWhatsAppConnected = isConnected, defaultPhone = defaultPhone))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun sendVideoViaCompanion(
        context: Context,
        videoFile: File,
        serverIp: String = getServerIp(context),
        phoneNumber: String = getPhoneNumber(context)
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phoneNumber.replace(Regex("\\D"), "")
            if (cleanPhone.isEmpty()) {
                return@withContext Result.failure(Exception("Phone number with country code is required"))
            }

            val url = "http://${serverIp.trim()}:3000/send-hd"
            val requestBody = videoFile.asRequestBody("video/mp4".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("x-target-phone", cleanPhone)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(bodyStr).optString("error", "HTTP error ${response.code}")
                    } catch (_: Exception) {
                        "HTTP error ${response.code}: $bodyStr"
                    }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val msg = try {
                    JSONObject(bodyStr).optString("message", "Video sent to WhatsApp!")
                } catch (_: Exception) {
                    "Video sent to WhatsApp!"
                }
                Result.success(msg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
