package org.jetbrains

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val API_URL = BuildConfig.API_URL
private val REMOTE_API_URL = BuildConfig.REMOTE_API_URL
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()
}
private val logger = Logger.getInstance("GitCommitMessagePlugin")

fun generateCommitMessageWithContext(
    beforeCode: String,
    afterCode: String,
    filename: String,
    apiToken: String,
    useProxy: Boolean = false,
    retry: Int = 10
): String? {
    val prompts = arrayListOf(
        """
        I have updated part of my code. Below are the details of the change, including the original code (before) and the updated code (after). Please generate a Git commit message that:
        
        - Follows the Conventional Commits format (type(scope): message)
        - Uses imperative mood ("add", "fix", "refactor", not "added" or "fixed")
        - Is clear and concise, summarizing what changed and (if possible) why
        - Includes a short commit message (72 characters or fewer)
        - (Optional) Suggests an extended description, if the change is complex



        📄 File: $filename
    """.trimIndent(), "Before:\n\n\n\n$beforeCode".trimIndent(), "After:\n\n\n\n$afterCode".trimIndent()
    )

    val history = mutableListOf<Message>()

    for (prompt in prompts) {
        val userMessage = Message(role = "user", content = prompt)
        val messages = history + userMessage

        var assistantMessage: Message? = null
        var attempts = 0

        while (assistantMessage == null && attempts < retry) {
            assistantMessage = runCatching{ completions(messages, apiToken, useProxy) }.getOrNull()
            attempts++
        }

        if (assistantMessage == null) return null

        history.add(userMessage)
        history.add(assistantMessage)
    }

    return history.lastOrNull { it.role == "assistant" }?.content
}

fun completions(messages: List<Message>, apiToken: String, useProxy: Boolean = false): Message? {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()

    // Build the original request payload
    val requestModel = RequestModel(
        stream = false,
        model = "chatgpt-4o-latest",
        messages = messages,
        features = Features(
            imageGeneration = false,
            codeInterpreter = false,
            webSearch = false
        ),
        modelItem = ModelItem(
            id = "chatgpt-4o-latest",
            `object` = "model",
            name = "chatgpt-4o-latest",
            urlIdx = 0
        )
    )

    val actualRequestJson = gson.toJson(requestModel)

    // Determine target URL and request body
    val (url, requestBodyJson) = if (useProxy) {
        val proxyPayload = mapOf(
            "target_url" to "$API_URL/api/chat/completions",
            "method" to "POST",
            "data" to gson.fromJson(actualRequestJson, Map::class.java),
            "headers" to mapOf(
                "Authorization" to "Bearer $apiToken",
                "Cookie" to "token=$apiToken"
            )
        )
        REMOTE_API_URL to gson.toJson(proxyPayload)
    } else {
        "$API_URL/api/chat/completions" to actualRequestJson
    }

    // Build the request
    val request = Request.Builder()
        .url(url)
        .post(requestBodyJson.toRequestBody(mediaType))
        .addHeader("Accept", "application/json")
        .addHeader("Content-Type", "application/json")
        .addHeader("Authorization", "Bearer $apiToken")
        .addHeader("Cookie", "token=$apiToken")
        .build()

    // Execute request
    val response = httpClient.newCall(request).execute()
    val responseBody = response.body.string()

    // Parse JSON response
    val chatResponse = Gson().fromJson(responseBody, ChatResponse::class.java)
    return chatResponse?.choices?.lastOrNull { it.message.role == "assistant" }?.message
}

fun login(email: String, password: String, useProxy: Boolean = false): String? {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()

    // Build the actual request body
    val requestBodyObj = SignInRequest(
        email = email,
        password = password
    )
    val jsonBody = gson.toJson(requestBodyObj)

    // Determine request target and payload
    val (url, finalRequestBody) = if (useProxy) {
        val proxyPayload = mapOf(
            "target_url" to "$API_URL/api/v1/auths/signin",
            "method" to "POST",
            "data" to gson.fromJson(jsonBody, Map::class.java)
            // You can add headers here if needed:
            // "headers" to mapOf("Content-Type" to "application/json")
        )
        REMOTE_API_URL to gson.toJson(proxyPayload).toRequestBody(mediaType)
    } else {
        "$API_URL/api/v1/auths/signin" to jsonBody.toRequestBody(mediaType)
    }

    // Build the request
    val request = Request.Builder()
        .url(url)
        .post(finalRequestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    // Execute and handle response
    val response = httpClient.newCall(request).execute()
    val responseBody = response.body.string()
    return try {
        val loginResponse = gson.fromJson(responseBody, SignInResponse::class.java)
        loginResponse?.token
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


fun auth(token: String, useProxy: Boolean = false): Boolean {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()

    // Prepare target URL
    val targetUrl = "$API_URL/api/v1/auths/"

    // Determine request URL and body
    val (url, request) = if (useProxy) {
        // POST to PHP proxy
        val proxyPayload = mapOf(
            "target_url" to targetUrl,
            "method" to "GET",
            "headers" to mapOf(
                "Authorization" to "Bearer $token",
                "Cookie" to "token=$token"
            )
        )

        val proxyBody = gson.toJson(proxyPayload).toRequestBody(mediaType)

        REMOTE_API_URL to Request.Builder()
            .url(REMOTE_API_URL)
            .post(proxyBody)
            .addHeader("Content-Type", "application/json")
            .build()
    } else {
        // Direct GET request
        targetUrl to Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Cookie", "token=$token")
            .build()
    }

    // Execute request
    val response = httpClient.newCall(request).execute()
    val json = response.body.string()

    return try {
        val authResponse = gson.fromJson(json, AuthResponse::class.java)
        authResponse.email.isNotEmpty()
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
