package org.jetbrains

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

fun completions(content: String, apiToken: String): String? {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()

    val requestModel = RequestModel(
        stream = false,
        model = "chatgpt-4o-latest",
        messages = listOf(Message(role = "user", content = content)),
        features = Features(imageGeneration = false, codeInterpreter = false, webSearch = false),
        modelItem = ModelItem(id = "chatgpt-4o-latest", `object` = "model", name = "chatgpt-4o-latest", urlIdx = 0)
    )

    val jsonString = gson.toJson(requestModel)

    val request = Request.Builder()
        .url(API_URL)
        .post(jsonString.toRequestBody(mediaType))
        .addHeader("accept", "application/json")
        .addHeader("content-type", "application/json")
        .addHeader(
            "authorization",
            "Bearer $apiToken"
        )
        .addHeader(
            "Cookie",
            "token=$apiToken"
        )
        .build()

    val response = httpClient.newCall(request).execute()
    val jsonResponse = response.body.string()
    val chatResponse = gson.fromJson(jsonResponse, ChatResponse::class.java)
    return chatResponse?.choices?.firstOrNull()?.message?.content
}

fun completions_remote(content: String, apiToken: String): String? {
    val gson = Gson()
    val mediaType = "application/json".toMediaType()

    logger.info("completions_remote content: $content")

    val requestBody = RemoteRequestBody(
        action = "generate_message",
        content = content,
        api_url = API_URL,
        api_token = apiToken
    )
    val jsonString = gson.toJson(requestBody)
    val request = Request.Builder()
        .url(REMOTE_API_URL)
        .post(jsonString.toRequestBody(mediaType))
        .addHeader("Content-Type", "application/json")
        .build()
    val response = httpClient.newCall(request).execute()
    try {
        val apiResponse: RemoteApiResponse? = gson.fromJson(response.body.string(), RemoteApiResponse::class.java)
        return apiResponse?.response
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun login(email: String, password: String): String? {
    val client = OkHttpClient()
    val gson = Gson()

    val requestBodyObj = SignInRequest(
        email = email,
        password = password
    )

    val jsonBody = gson.toJson(requestBodyObj)
    val mediaType = "application/json".toMediaType()
    val requestBody = jsonBody.toRequestBody(mediaType)

    val request = Request.Builder()
        .url("$API_URL/api/v1/auths/signin")
        .post(requestBody)
        .addHeader("content-type", "application/json")
        .build()
    val response = client.newCall(request).execute()
    return try {
        val loginResponse = Gson().fromJson(response.body.string(), SignInResponse::class.java)
        return loginResponse.token
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun login_remote(email: String, password: String): String? {
    val gson = Gson()
    val requestBody = RemoteRequestBody(
        action = "login",
        api_url = API_URL,
        email = email,
        password = password,
    )
    val jsonString = gson.toJson(requestBody)
    val mediaType = "application/json".toMediaType()

    val request = Request.Builder()
        .url(REMOTE_API_URL)
        .post(jsonString.toRequestBody(mediaType))
        .addHeader("Content-Type", "application/json")
        .build()
    val response = httpClient.newCall(request).execute()
    return try {
        val loginResponse = gson.fromJson(response.body.string(), SignInResponse::class.java) ?: return null
        loginResponse.token
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun auth(token: String): Boolean {
    val request = Request.Builder()
        .url(API_URL + "/api/v1/auths/")
        .get()
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Content-Type", "application/json")
        .addHeader("Cookie", "token=$token")
        .build()

    val response = httpClient.newCall(request).execute()
    return try {
        val authResponse = Gson().fromJson(response.body.string(), AuthResponse::class.java)
        authResponse.email.isNotEmpty()
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun auth_remote(token: String): Boolean {
    val gson = Gson()
    val requestBody = RemoteRequestBody(
        action = "auth",
        api_url = API_URL,
        api_token = token
    )
    val jsonString = gson.toJson(requestBody)
    val mediaType = "application/json".toMediaType()
    val request = Request.Builder()
        .url(REMOTE_API_URL)
        .post(jsonString.toRequestBody(mediaType))
        .addHeader("Content-Type", "application/json")
        .build()
    val response = httpClient.newCall(request).execute()
    return try {
        val authResponse = Gson().fromJson(response.body.string(), AuthResponse::class.java)
        authResponse.email.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
