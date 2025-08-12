package org.jetbrains

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.changes.Change
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream


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
    files: List<Change>,
    apiToken: String,
    useProxy: Boolean = false,
    onMessage: (String) -> Unit,
    onComplete: (String?) -> Unit,
    onFailure: (String) -> Unit
) {
    try {
        val messages = arrayListOf(
            """
            I have updated part of my code (${files} files).
            Below are the details of the change, including the original code (before) and the updated code (after).
            Please generate a Git commit message that:
            
            - Follows the Conventional Commits format (type(scope): message)
            - Uses imperative mood ("add", "fix", "refactor", not "added" or "fixed")
            - Is clear and concise, summarizing what changed and (if possible) why
            - Includes a short commit message (72 characters or fewer)
            - Do not include any general descriptions or extended description of the commit format or instructions.
        """
        )

        files.forEach { file ->
            val path = file.virtualFile?.canonicalPath ?: return@forEach
            val before = file.beforeRevision?.content ?: return@forEach
            val after = file.afterRevision?.content ?: return@forEach

            val (trimmedBefore, trimmedAfter) = trimDiffPair(before, after)
            messages.add("File $path before change (original code):\\n\\n\\n\\n$trimmedBefore")
            messages.add("File $path after change (updated code):\\n\\n\\n\\n$trimmedAfter")
        }

        completions(
            messages = messages.map { Message("user", it.trim()) }, apiToken, useProxy,
            onMessage = onMessage,
            onFailure = onFailure,
            onComplete = onComplete
        )
    } catch (e: Exception) {
        onFailure(e.message ?: "An error occurred while attempting to generate the commit message")
    }

}

fun completions(
    messages: List<Message>,
    apiToken: String,
    useProxy: Boolean = false,
    onMessage: (String) -> Unit,
    onComplete: (String?) -> Unit,
    onFailure: (String) -> Unit
) {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()
    val model = PluginSettingsService.getInstance().state.selectedModel

    // Build the original request payload
    val requestModel = RequestModel(
        stream = true,
        model = model,
        messages = messages,
        features = Features(
            imageGeneration = false,
            codeInterpreter = false,
            webSearch = false
        ),
        modelItem = ModelItem(
            id = model,
            `object` = "model",
            name = model,
            urlIdx = 0
        )
    )

    val actualRequestJson = gson.toJson(requestModel)

    val targetUrl = "$API_URL/api/chat/completions"
    val (url, requestBodyJson) = if (useProxy) {
        val proxyPayload = mapOf(
            "target_url" to targetUrl,
            "method" to "POST",
            "data" to gson.fromJson(actualRequestJson, Map::class.java),
            "headers" to mapOf(
                "Authorization" to "Bearer $apiToken",
                "Cookie" to "token=$apiToken"
            )
        )
        REMOTE_API_URL to gson.toJson(proxyPayload)
    } else {
        targetUrl to actualRequestJson
    }

    // Build the request
    val request = Request.Builder()
        .url(url)
        .post(requestBodyJson.toByteArray().toRequestBody(mediaType))
        .addHeader("Accept", "text/event-stream")
        .addHeader("Content-Type", "application/json")
        .addHeader("Authorization", "Bearer $apiToken")
        .addHeader("Cookie", "token=$apiToken")
        .build()

    val factory = EventSources.createFactory(httpClient)
    factory.newEventSource(request, object : EventSourceListener() {
        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            if ("[DONE]" == data) {
//                eventSource.cancel()
            } else {
                val chunk = Gson().fromJson(data, ChatCompletionChunk::class.java)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null) {
                    onMessage(content)
                }
            }
        }

        override fun onClosed(eventSource: EventSource) {
            onComplete(null)
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            logger.warn("SSE Failure: ", t)
            val body = response?.body?.string()
            val error = try {
                Gson().fromJson(body, ErrorResponse::class.java)?.detail
            } catch (e: Exception) {
                logger.warn("Failed to read error response body", e)
                null
            }
            onFailure("Completions SSE Failure: " + (error ?: body ?: ""))
        }
    })
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

    val targetUrl = "$API_URL/api/v1/auths/signin"
    val (url, finalRequestBody) = if (useProxy) {
        val proxyPayload = mapOf(
            "target_url" to targetUrl,
            "method" to "POST",
            "data" to gson.fromJson(jsonBody, Map::class.java)
            // You can add headers here if needed:
            // "headers" to mapOf("Content-Type" to "application/json")
        )
        REMOTE_API_URL to gson.toJson(proxyPayload).toRequestBody(mediaType)
    } else {
        targetUrl to jsonBody.toRequestBody(mediaType)
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
    val headers = mapOf(
        "Authorization" to "Bearer $token",
        "Cookie" to "token=$token"
    )

    val request = if (useProxy) {
        val proxyPayload = mapOf(
            "target_url" to targetUrl,
            "method" to "GET",
            "headers" to headers
        )
        val proxyBody = gson.toJson(proxyPayload).toRequestBody(mediaType)

        Request.Builder()
            .url(REMOTE_API_URL)
            .post(proxyBody)
            .addHeader("Content-Type", "application/json")
            .build()
    } else {
        Request.Builder()
            .url(targetUrl)
            .get()
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
                addHeader("Content-Type", "application/json")
            }
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

fun getModels(
    apiToken: String,
    useProxy: Boolean = false
): List<ModelInfo>? {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()
    val targetUrl = "$API_URL/api/models"

    // Headers for direct request
    val headers = mapOf(
        "Accept" to "application/json",
        "Authorization" to "Bearer $apiToken",
        "Cookie" to "token=$apiToken"
    )

    // Building request depending on proxy usage
    val request = if (useProxy) {
        val proxyPayload = mapOf(
            "target_url" to targetUrl,
            "method" to "GET",
            "headers" to headers
        )
        val proxyBody = gson.toJson(proxyPayload).toRequestBody(mediaType)
        Request.Builder()
            .url(REMOTE_API_URL)
            .post(proxyBody)
            .addHeader("Content-Type", "application/json")
            .build()
    } else {
        Request.Builder()
            .url(targetUrl)
            .get()
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()
    }

    return try {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn("Failed to fetch models: ${response.code} ${response.message}")
                return null
            }

            val json = response.body?.string() ?: return null
            val modelsResponse = gson.fromJson(json, ModelsResponse::class.java)

            val settings = PluginSettingsService.getInstance()
            settings.state.models = modelsResponse.data

            modelsResponse.data
        }
    } catch (e: Exception) {
        logger.warn("Error fetching models", e)
        null
    }
}

fun gzip(data: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).use { it.write(data) }
    return bos.toByteArray()
}
