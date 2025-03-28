package org.jetbrains

import com.google.gson.annotations.SerializedName


// Define data classes to match the JSON structure
data class RequestModel(
    val stream: Boolean,
    val model: String,
    val messages: List<Message>,
    val features: Features,
    @SerializedName("model_item") val modelItem: ModelItem
)

data class Message(
    val role: String,
    val content: String
)

data class Features(
    @SerializedName("image_generation") val imageGeneration: Boolean,
    @SerializedName("code_interpreter") val codeInterpreter: Boolean,
    @SerializedName("web_search") val webSearch: Boolean
)

data class ModelItem(
    val id: String,
    val `object`: String,
    val name: String,
    val urlIdx: Int
)


// Define data classes to match the JSON structure
data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage,
    @SerializedName("service_tier") val serviceTier: String,
    @SerializedName("system_fingerprint") val systemFingerprint: String
)

data class Choice(
    val index: Int,
    val message: Message,
    val logprobs: Any?,
    @SerializedName("finish_reason") val finishReason: String
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: TokenDetails,
    @SerializedName("completion_tokens_details") val completionTokensDetails: TokenDetails
)

data class TokenDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int?,
    @SerializedName("audio_tokens") val audioTokens: Int?,
    @SerializedName("reasoning_tokens") val reasoningTokens: Int?,
    @SerializedName("accepted_prediction_tokens") val acceptedPredictionTokens: Int?,
    @SerializedName("rejected_prediction_tokens") val rejectedPredictionTokens: Int?
)